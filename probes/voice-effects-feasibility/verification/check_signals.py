from pathlib import Path
import array,json,math,statistics,sys,wave

def tone_frequency(samples,rate):
    # Integer periods from upward zero crossings; exclude padded/crossfade boundaries.
    a=round(rate*0.2);b=len(samples)-a
    edges=[]
    for i in range(a,b-1):
        if samples[i]<=0 and samples[i+1]>0:
            edges.append(i-samples[i]/(samples[i+1]-samples[i]))
    periods=[b-a for a,b in zip(edges,edges[1:])]
    if not periods:raise ValueError('no tone energy')
    return rate/statistics.median(periods),edges

rows=[]
for path in sorted(Path(sys.argv[1]).glob('*.f32')):
    rate,freq,semi=map(int,path.stem.split('_'));v=array.array('f');v.frombytes(path.read_bytes())
    if sys.byteorder!='little':v.byteswap()
    hz,edges=tone_frequency(v,rate);want=freq*2**(semi/12)
    rel=abs(hz-want)/want;rms=math.sqrt(sum(x*x for x in v)/len(v))
    passed=len(v)==rate*2 and rel<0.02 and 0.01<rms<0.71 and max(map(abs,v))<=1.001
    rows.append({'file':path.name,'frames':len(v),'frequency':hz,'expected':want,'relativeError':rel,'rms':rms,'passed':passed})
    out=path.with_suffix('.wav')
    pcm=array.array('h',(max(-32768,min(32767,round(x*32767))) for x in v))
    if sys.byteorder!='little':pcm.byteswap()
    with wave.open(str(out),'wb') as w:w.setparams((1,2,rate,0,'NONE','not compressed'));w.writeframes(pcm.tobytes())
receipt={'status':'passed' if len(rows)==27 and all(r['passed'] for r in rows) else 'failed','renders':len(rows),'maxRelativePitchError':max((r['relativeError'] for r in rows),default=None),'rows':rows,'boundary':'synthetic steady tones only; not speech quality or device acceptance'}
Path(sys.argv[2]).write_text(json.dumps(receipt,indent=2));print(json.dumps(receipt,indent=2));sys.exit(0 if receipt['status']=='passed' else 1)
