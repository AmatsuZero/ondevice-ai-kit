from pathlib import Path
import array,hashlib,json,math,statistics,struct,subprocess,sys,time,wave

task=Path(sys.argv[1]);name=sys.argv[2];cmd=json.loads(sys.argv[3]);fixtures=task/'fixtures';out=task/(name+'-file-acceptance');out.mkdir()
cases=[]
def invoke(inp,dest,semi,ok):
    before=hashlib.sha256(inp.read_bytes()).hexdigest(); files=set(out.iterdir());tick=time.monotonic()
    r=subprocess.run(cmd+[str(inp),str(dest),str(semi)],capture_output=True,text=True)
    same=hashlib.sha256(inp.read_bytes()).hexdigest()==before
    added=set(out.iterdir())-files
    leftovers=[p.name for p in added if p!=dest]
    result={'input':inp.name,'output':dest.name,'semitones':semi,'exit':r.returncode,'elapsedSeconds':time.monotonic()-tick,'stdout':r.stdout,'stderr':r.stderr,'inputUnchanged':same,'unexpectedFiles':leftovers}
    result['passed']=same and not leftovers and ((r.returncode==0 and dest.is_file()) if ok else (r.returncode!=0 and not dest.exists()))
    return result

for rate in [16000,44100,48000]:
    for freq in [220,440,880]:
        for semi in [0,4,-4]:
            source=fixtures/f'{rate}_{freq}.wav';dest=out/f'{rate}_{freq}_{semi}.wav';r=invoke(source,dest,semi,True)
            if dest.exists():
                with wave.open(str(dest),'rb') as w:
                    params=w.getparams();v=array.array('h',w.readframes(w.getnframes()))
                if sys.byteorder!='little':v.byteswap()
                lo=int(rate*.2);hi=len(v)-lo
                edges=[i-v[i]/(v[i+1]-v[i]) for i in range(lo,hi-1) if v[i]<=0<v[i+1]]
                hz=rate/statistics.median(b-a for a,b in zip(edges,edges[1:])) if len(edges)>1 else 0
                wanted=freq*2**(semi/12);error=abs(hz-wanted)/wanted
                r.update(frames=len(v),sampleRate=params.framerate,channels=params.nchannels,sampleWidth=params.sampwidth,frequency=hz,expectedFrequency=wanted,relativePitchError=error)
                r['passed'] &= len(v)==rate*2 and params.framerate==rate and params.nchannels==1 and params.sampwidth==2 and error<.02
                if semi==0:r['bypassExact']=dest.read_bytes()==source.read_bytes();r['passed'] &= r['bypassExact']
            cases.append(r)
    for kind in ['silence','one','max-silence']:
        for semi in [0,4,-4]:
            source=fixtures/f'{rate}_{kind}.wav';dest=out/f'{rate}_{kind}_{semi}.wav';r=invoke(source,dest,semi,True)
            if dest.exists():
                with wave.open(str(source),'rb') as w:expected=w.getnframes()
                with wave.open(str(dest),'rb') as w:frames=w.getnframes();samples=w.readframes(frames)
                r.update(frames=frames,expectedFrames=expected);r['passed'] &= frames==expected
                if 'silence' in kind:r['silenceExact']=not any(samples);r['passed'] &= r['silenceExact']
            cases.append(r)

valid=(fixtures/'16000_440.wav').read_bytes()
mutants={'empty':b'','truncated':valid[:42], 'trailing':valid+b'xx','bad-riff':b'FAIL'+valid[4:]}
for label,offset,fmt,value in [('wrong-riff-size',4,'<I',1),('fmt-size',16,'<I',18),('not-pcm',20,'<H',3),('stereo',22,'<H',2),('rate',24,'<I',8000),('byte-rate',28,'<I',1),('block-align',32,'<H',4),('bit-depth',34,'<H',8),('data-size',40,'<I',0)]:
    b=bytearray(valid);struct.pack_into(fmt,b,offset,value);mutants[label]=bytes(b)
over=bytearray(valid[:44]+b'\0'*(16000*30*2+2));struct.pack_into('<I',over,4,len(over)-8);struct.pack_into('<I',over,40,len(over)-44);mutants['over30']=bytes(over)
for label,data in mutants.items():
    source=out/(label+'.input.wav');source.write_bytes(data);cases.append(invoke(source,out/(label+'.out.wav'),4,False))
for semi in [1,3,5,-3]:cases.append(invoke(fixtures/'16000_440.wav',out/f'invalid-{semi}.wav',semi,False))
dest=out/'existing.wav';dest.write_bytes(b'EXISTING');r=invoke(fixtures/'16000_440.wav',dest,4,True);r['passed']=r['exit']!=0 and dest.read_bytes()==b'EXISTING' and r['inputUnchanged'] and not r['unexpectedFiles'];cases.append(r)
source=out/'same.wav';source.write_bytes(valid);r=invoke(source,source,4,True);r['passed']=r['exit']!=0 and source.read_bytes()==valid and not r['unexpectedFiles'];cases.append(r)
receipt={'name':name,'status':'passed' if all(r['passed'] for r in cases) else 'failed','caseCount':len(cases),'passed':sum(r['passed'] for r in cases),'cases':cases,'boundary':'isolated synthetic host file rendering; no SDK lifecycle, human speech, physical-device or release acceptance'}
(task/(name+'-file-acceptance.json')).write_text(json.dumps(receipt,ensure_ascii=False,indent=2))
print(json.dumps({'name':name,'status':receipt['status'],'cases':len(cases),'passed':receipt['passed'],'failed':[r for r in cases if not r['passed']]},ensure_ascii=False,indent=2));sys.exit(0 if receipt['status']=='passed' else 1)
