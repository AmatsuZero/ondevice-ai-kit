"""Host fixture: real fake-ADB children operate only on their private temp tree."""
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[2]
OBS=ROOT/'probes/android-terminal-observation'
sys.path.insert(0,str(OBS))
from terminal_file_io import read_stable,write_new_durable

APP='io.ondeviceai.acceptance.qwenterminal'
TEST=APP+'.test'
RUN='w3-qwen-terminal-20261112-01'
FP='ff5b57b0b1ff08d8d8c984efd04dfbb86f62a5cc4cab02700c5804a0c7c0c3c5'
STAGE='/data/local/tmp/'+RUN
MODEL='no_backup/qwen3-asr-0.6b-int8-393f8a14'
MODELS=('conv_frontend.onnx','decoder.int8.onnx','encoder.int8.onnx',
        'tokenizer/merges.txt','tokenizer/tokenizer_config.json','tokenizer/vocab.json')
FAKE=r'''
import hashlib,json,os,shlex,shutil,sys
from pathlib import Path
p=Path(__file__).resolve().parent
APP='io.ondeviceai.acceptance.qwenterminal';TEST=APP+'.test'
RUN='w3-qwen-terminal-20261112-01';STAGE='/data/local/tmp/'+RUN
args=sys.argv[1:];assert args[:2]==['-s','serial-01'];a=args[2:]
with (p/'calls.jsonl').open('a') as f:f.write(json.dumps(a)+'\n')
out=p/'out';device=p/'device'
def mapped(s,app=None):
 if s.startswith('/data/local/tmp/'):return device/'stage'/s.removeprefix('/data/local/tmp/')
 if s.startswith('/data/app/'):
  alias=s.split('/')[3]
  return device/'ordinary.apk' if alias=='io.ondeviceai.demo' else device/'apps'/alias/'base.apk'
 assert app is not None and not s.startswith('/') and '..' not in s.split('/'),s
 return device/'apps'/app/s
def claim(name):
 v=json.loads((out/name).read_text());assert v['runId']==RUN
 return v
def mutate(label):
 with (p/'mutations.jsonl').open('a') as f:f.write(json.dumps({'label':label,'args':a})+'\n')
def fault(label):
 if (p/'fault.txt').exists() and (p/'fault.txt').read_text()==label and not (p/'fault-consumed').exists():
  (p/'fault-consumed').touch();sys.exit(19)
if a[0]=='push':
 v=claim('ownership-stage-claimed.json');target=a[2]
 assert target.removeprefix(STAGE+'/') in v['children']
 dest=mapped(target);dest.write_bytes(Path(a[1]).read_bytes()[:7] if (p/'fault.txt').exists() and (p/'fault.txt').read_text()=='partial-push' else Path(a[1]).read_bytes())
 mutate('push');fault('partial-push');print('1 file pushed');sys.exit(0)
assert a[0] in ('shell','exec-out'),a
tokens=shlex.split(a[1]) if len(a)==2 else a[1:]
if tokens[:2]==['getprop','ro.product.model']:print('TECNO KM9');sys.exit(0)
if tokens[:2]==['getprop','ro.build.version.sdk']:print('35');sys.exit(0)
if tokens[:2]==['getprop','ro.product.cpu.abi']:print('arm64-v8a');sys.exit(0)
if tokens==['dumpsys','activity','processes']:print('No active instrumentation');sys.exit(0)
if tokens==['dumpsys','power']:print('mWakefulness=Awake');sys.exit(0)
if tokens==['dumpsys','window','policy']:
 print('showing=true' if (p/'locked').exists() else 'showing=false');sys.exit(0)
if tokens==['am','get-current-user']:print('0');sys.exit(0)
if tokens==['cat','/proc/meminfo']:print('MemAvailable: 3145728 kB');sys.exit(0)
if tokens[:3]==['pm','list','packages']:
 prefix=tokens[3] if len(tokens)>3 else '';aliases=['io.ondeviceai.demo']+[d.name for d in (device/'apps').iterdir() if (d/'base.apk').exists()]
 print('\n'.join('package:'+alias for alias in sorted(aliases) if prefix in alias));sys.exit(0)
if tokens[:2]==['pm','path']:
 f=mapped('/data/app/'+tokens[2]+'/base.apk')
 if f.exists():print('package:/data/app/'+tokens[2]+'/base.apk')
 sys.exit(0)
if tokens[:3]==['pm','list','instrumentation']:
 print('instrumentation:'+TEST+'/io.ondeviceai.acceptance.qwenfile.QwenTerminalInstrumentation (target='+APP+')');sys.exit(0)
if tokens[:3]==['pm','install','-t']:
 source=mapped(tokens[3]);alias=APP if source.name=='main.apk' else TEST
 claim('ownership-install-'+('main' if alias==APP else 'test')+'-claimed.json')
 dest=device/'apps'/alias/'base.apk';dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,dest)
 mutate('install');fault('after-main-install' if alias==APP else 'after-test-install');print('Success');sys.exit(0)
if tokens[:2]==['pm','uninstall']:
 alias=tokens[2];assert alias in (APP,TEST);shutil.rmtree(device/'apps'/alias)
 mutate('uninstall');print('Success');sys.exit(0)
if tokens[:3]==['am','force-stop',APP]:mutate('force-stop');sys.exit(0)
if tokens[:3]==['am','start','-n']:
 assert tokens[3]=='io.ondeviceai.demo/io.ondeviceai.demo.MainActivity';mutate('return-demo');print('Starting: Intent');sys.exit(0)
app=None
if tokens[0]=='run-as':app=tokens[1];tokens=tokens[2:]
if tokens[:2]==['cat','/proc/self/status']:
 assert app==APP;print('VmRSS: 123 kB');sys.exit(0)
if tokens[0]=='test':
 assert len(tokens)==3
 f=mapped(tokens[2],app);flag=tokens[1]
 okay={'-e':f.exists(),'-L':f.is_symlink(),'-f':f.is_file(),'-d':f.is_dir()}[flag]
 sys.exit(0 if okay else 1)
if tokens[0]=='mkdir':
 target=tokens[-1];f=mapped(target,app)
 if app is None:claim('ownership-stage-claimed.json')
 f.mkdir(parents=('-p' in tokens),exist_ok=('-p' in tokens));mutate('mkdir');fault('after-stage-mkdir' if app is None else 'after-private-mkdir');sys.exit(0)
if tokens[0]=='printf':
 assert '>' in tokens;idx=tokens.index('>');target=tokens[idx+1];v=claim('ownership-stage-claimed.json')
 assert target==STAGE+'/.odai-owner'
 value=tokens[2] if tokens[1] in ('%s','%s\\n') else tokens[1]
 if tokens[1]=='%s\\n':value+='\n'
 mapped(target).write_bytes(value.encode());mutate('owner');fault('after-owner');sys.exit(0)
if tokens[0]=='cat' and '>' in tokens:
 idx=tokens.index('>');dest=mapped(tokens[idx+1]);v=claim('ownership-stage-claimed.json');assert dest.name in v['children']
 dest.write_bytes(b''.join(mapped(s).read_bytes() for s in tokens[1:idx]));mutate('concat');fault('after-concat');sys.exit(0)
if tokens[0]=='sha256sum':
 f=mapped(tokens[1],app);print(hashlib.sha256(f.read_bytes()).hexdigest()+'  '+tokens[1]);sys.exit(0)
if tokens[:2]==['head','-c']:
 f=mapped(tokens[3],app);sys.stdout.buffer.write(f.read_bytes()[:int(tokens[2])]);sys.exit(0)
if tokens[:2]==['stat','-c']:
 f=mapped(tokens[3],app);s=f.stat();print(str(s.st_uid)+' '+str(s.st_dev)+' '+str(s.st_ino));sys.exit(0)
if tokens[0]=='cp':
 assert app==APP;src=mapped(tokens[1]);dest=mapped(tokens[2],app);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(src,dest);mutate('copy');fault('after-model-copy');sys.exit(0)
if tokens[:2]==['ls','-1'] or tokens[:2]==['ls','-A']:
 f=mapped(tokens[2],app);print('\n'.join(sorted(x.name for x in f.iterdir())));sys.exit(0)
if tokens[:2]==['rm','-f']:
 assert len(tokens)==3 and tokens[2].startswith(STAGE+'/');mapped(tokens[2]).unlink();mutate('remove-file');sys.exit(0)
if tokens[0]=='rmdir':
 assert tokens[1]==STAGE;mapped(tokens[1]).rmdir();mutate('remove-stage');sys.exit(0)
raise AssertionError(a)
'''


class PrepareFixture:
    def __init__(self,test):
        self.test=test;self.temp=tempfile.TemporaryDirectory(prefix='terminal-prepare-parent-');test.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name).resolve();self.out=self.root/'out';self.out.mkdir()
        self.device=self.root/'device';(self.device/'apps').mkdir(parents=True);(self.device/'stage').mkdir()
        (self.device/'ordinary.apk').write_bytes(b'original ordinary app')
        self.adb=self.root/'adb';self.adb.write_text('#!'+sys.executable+'\n'+FAKE);self.adb.chmod(0o700)
        self.evidence=self.root/'evidence';self.evidence.mkdir();self.models=self.root/'models';self.models.mkdir()
        for name in MODELS:
            f=self.models/name;f.parent.mkdir(exist_ok=True);f.write_bytes(('public fake '+name).encode())
        self.package={'sourceFingerprint':FP,'files':{}}
        for name,alias in [('main.apk',APP),('test.apk',TEST)]:
            f=self.out/name;f.write_bytes(('synthetic '+name).encode());self.package['files'][name]={'package':alias,'bytes':f.stat().st_size,'sha256':self.sha(f)}
        self.manifest={'directory':str(self.models),'files':{name:{'bytes':(self.models/name).stat().st_size,'sha256':self.sha(self.models/name)} for name in MODELS}}
        self.write_json(self.evidence/'model-manifest.json',self.manifest)
        self.write_json(self.evidence/'android-target-local.json',{'serial':'serial-01'})
        self.write_json(self.out/'package-verified.json',self.package)
        self.write_json(self.out/'candidate-accepted.json',{'sourceFiles':{}})
        self.write_json(self.out/'production-before.json',{})
        self.write_json(self.out/'run-plan.json',{'runId':RUN,'sourceFingerprint':FP,'manifestSHA256':'8d1cfd7465e70f413b741701250d61c31170b25a6978a256a4df0349efa0910f','maxNativeDecodeCalls':2,'caseCount':1})
        spec=importlib.util.spec_from_file_location('real_terminal_source_adapter',ROOT/'probes/android-qwen-terminal/terminal_source_adapter.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
        ns={'__name__':'prepare_fixture_source','__file__':str(ROOT/'probes/android-qwen-file/device_acceptance.py')};exec(compile(m.adapt_source((ROOT/'probes/android-qwen-file/device_acceptance.py').read_text()),'<real adapted FILE>','exec'),ns)
        ns.update(ROOT=self.root,OUT=self.out,EVIDENCE=self.evidence)
        self.guards=0
        def guard():self.guards+=1
        ns['_terminal_before_transport']=guard
        # These two safe helpers live at module scope, outside main's locals().
        # The future outer dispatcher must explicitly pass the real definitions.
        ns['_terminal_dispatch']=lambda action,context:dict(context,digest=ns['digest'],parse_kb=ns['parse_kb'])
        home=self.root/'home';tools=home/'Library/Android/sdk/platform-tools';tools.mkdir(parents=True);(tools/'adb').symlink_to(self.adb)
        with patch.object(Path,'home',return_value=home):self.context=ns['main']('prepare')
        self.before_ordinary=self.context['ordinary']();self.before_models=self.context['ordinary_models']()
        self.cleanup_calls=0
        self.mutation_start=len(self.mutations())

    @staticmethod
    def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
    @staticmethod
    def write_json(path,value):path.write_text(json.dumps(value,indent=2)+'\n')
    def mutations(self):return [json.loads(s) for s in (self.root/'mutations.jsonl').read_text().splitlines()] if (self.root/'mutations.jsonl').exists() else []
    def cleanup(self):self.cleanup_calls+=1
    def fault(self,label):(self.root/'fault.txt').write_text(label)
    def call(self,module,**kw):
        args={'serial':'serial-01','cleanup':self.cleanup,'nonce_factory':lambda:'1'*32};args.update(kw)
        return module.prepare(self.out,self.context,**args)
