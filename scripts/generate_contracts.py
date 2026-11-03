"""Generate checked-in, strict JSON contracts and an original local audio asset. No network."""
from pathlib import Path
import json, math, wave, struct
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'contracts'
OUT.mkdir(exist_ok=True)
def obj(**props): return dict(type='object', properties=props, required=list(props), additionalProperties=False)
def enum(values): return dict(type='string', enum=values.split())
def nullable(s): return dict(anyOf=[s, dict(type='null')])
def ref(n): return {'$ref': '#/$defs/'+n}
def arr(s, maximum=128): return dict(type='array', items=s, maxItems=maximum)
S=dict(type='string', maxLength=1000)
N=dict(type='number'); I=dict(type='integer'); B=dict(type='boolean')
defs={}
defs['condition']=obj(field=enum('OUTSIDE_TEMPERATURE GEAR SPEED SEAT_OCCUPIED TIME_WINDOW WEEKDAY'),operator=enum('EQ NE LT LTE GT GTE'),value=nullable(N),text=nullable(S),flag=nullable(B),startMinute=nullable(I),endMinute=nullable(I),weekdays=arr(I,7))
defs['trigger']=obj(type=enum('MANUAL VOICE SEAT_OCCUPIED TIME CONDITION_ENTER'),condition=nullable(ref('condition')),filters=arr(ref('condition'),16),minuteOfDay=nullable(I),weekdays=arr(I,7),zoneId=S)
defs['action']=obj(type=enum('NAVIGATE CANCEL_NAVIGATION SET_HVAC_TARGET SET_AMBIENT_BRIGHTNESS SET_AMBIENT_COLOR SET_SEAT_HEAT MOVE_SEAT_LIMITED PLAY_LOCAL_AUDIO STOP_LOCAL_AUDIO OPEN_APP MEDIA_CONTROL CALENDAR_READ HA_CALL PLACE_SEARCH WHAT3WORDS_RESOLVE'),area=S,value=nullable(N),text=nullable(S),targetId=nullable(S),latitude=nullable(N),longitude=nullable(N),axis=nullable(enum('FORE_AFT BACKREST')),direction=nullable(enum('FORWARD BACKWARD')))
defs['node']=obj(id=S,kind=enum('START ACTION IF WAIT TIMER REPEAT PARALLEL JOIN END'),label=S,position=obj(x=N,y=N),action=nullable(ref('action')),condition=nullable(ref('condition')),durationSeconds=nullable(I),repeatCount=nullable(I),joinId=nullable(S))
defs['edge']=obj(id=S,source=S,target=S,port=S)
defs['workflow']=obj(schemaVersion=dict(type='integer',enum=[2]),id=S,title=S,revision=dict(type='integer',minimum=1),trigger=ref('trigger'),nodes=arr(ref('node'),64),edges=arr(ref('edge'),192))
defs['operation']=obj(type=enum('REPLACE_NODE ADD_NODE REMOVE_NODE CONNECT DISCONNECT CHANGE_TITLE CHANGE_TRIGGER'),node=nullable(ref('node')),nodeId=nullable(S),edge=nullable(ref('edge')),edgeId=nullable(S),title=nullable(S),trigger=nullable(ref('trigger')))
defs['patch']=obj(baseRevision=dict(type='integer',minimum=1),operations=arr(ref('operation'),128))
def write(name,data): (OUT/name).write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
write('workflow.schema.json',dict(**ref('workflow'), **{'$defs':defs}))
response=obj(status=enum('draft needs_clarification unsupported'),explanation=S,questions=arr(S,5),workflow=nullable(ref('workflow')),patch=nullable(ref('patch')))
response['$defs']=defs
write('planner-response.schema.json',response)
def fields(name, **values): return {**dict.fromkeys(defs[name]['properties']), **values}
def condition(field, **values): return fields('condition',**dict(field=field,operator='LTE',weekdays=[]) | values)
def node(id,kind,label,x,y,**values): return fields('node',id=id,kind=kind,label=label,position=dict(x=x,y=y),**values)
fixture=fields('workflow',schemaVersion=2,id='contract-commute',title='출근 계약 시험',revision=1,
    trigger=fields('trigger',type='SEAT_OCCUPIED',filters=[condition('WEEKDAY',weekdays=[1,2,3,4,5]),condition('TIME_WINDOW',startMinute=420,endMinute=540)],weekdays=[],zoneId='Asia/Seoul'),
    nodes=[node('start','START','탑승',40,160),node('navigation','ACTION','시험 목적지 안내',300,160,action=fields('action',type='NAVIGATE',area='ROW_1_LEFT',text='서울시청 공개 좌표 · 시험용',latitude=37.5665,longitude=126.978)),node('cold','IF','5도 이하',560,160,condition=condition('OUTSIDE_TEMPERATURE',value=5)),node('heat','ACTION','앞줄 왼쪽 열선',820,60,action=fields('action',type='SET_SEAT_HEAT',area='ROW_1_LEFT',value=2)),node('end','END','완료',1080,160)],
    edges=[dict(id='e1',source='start',target='navigation',port='next'),dict(id='e2',source='navigation',target='cold',port='next'),dict(id='e3',source='cold',target='heat',port='true'),dict(id='e4',source='cold',target='end',port='false'),dict(id='e5',source='heat',target='end',port='next')])
(OUT/'fixtures').mkdir(exist_ok=True)
write('fixtures/commute.json',fixture)
vehicle_source='https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro'
entries=[]
def entry(id,name,actions,status,note,source): entries.append(dict(id=id,name=name,actions=actions,status=status,note=note,source=source,checkedAt='2026-10-27',accountStatus='미확인',runtimeStatus='미검증'))
for name,ko in [('EvBattery','배터리'),('HVAC','공조'),('Door','도어'),('Seat','시트'),('Window','창문'),('Display','디스플레이'),('Odometer','주행 거리'),('Light','조명'),('Safety','안전 상태'),('CarInfo','차량 정보'),('DrivingMode','주행 모드'),('Gear','기어'),('SteeringWheel','스티어링 휠'),('Toll','통행료'),('TurnSignal','방향 지시등'),('Wiper','와이퍼'),('Tire','타이어'),('SideMirror','사이드 미러'),('Brake','브레이크'),('Fuel','연료')]:
    implemented=name in ['HVAC','Seat','Light','Gear']
    entry('vehicle.'+name,'Pleos '+ko,[{'HVAC':'목표 온도','Seat':'열선·제한 이동·STOP','Light':'앰비언트 밝기·색상','Gear':'기어 읽기'}.get(name,'공식 SDK 기능 조회')], '타입 연결 구현 · 런타임 검증 필요' if implemented else '공식 SDK 도메인 · 루틴 실행 미개방', '2.3.2 소스의 getter 기준. 문서 공개는 계정 권한의 증거가 아닙니다. 주행·제동·조향 제어는 일반 루틴 허용 목록에 없습니다.', vehicle_source)
launch='https://www.hyundaimotorgroup.com/ko/news/hyundai-motor-group-redefines-in-vehicle-experience-with-pleos-connect-next-generation-infotainment-system'
new='https://www.hyundaimotorgroup.com/ko/news/42dot-pleos-connect-app-market-new-apps'
for i,name in enumerate(['네이버 오토','네이버 지도','YouTube','Spotify','SBS고릴라','essential;','지니','카카오내비','what3words','Make My Day - Route Planner','멜론','Radioline','RaiDIO Auto','픽클']):
    entry('market.'+str(i),name,['설치된 앱 열기 후보','미디어 세션 연결 가능 여부 확인'],'정책 차단' if name=='Spotify' else '마켓 입점 확인 · 자동화 API는 별도', 'Spotify 음성 제어는 사전 서면 승인 전 개방하지 않습니다.' if name=='Spotify' else '마켓 18종 중 보도자료에서 이름을 확인한 14종입니다. 모바일 SDK가 Pleos 앱을 제어한다는 뜻은 아닙니다. 설치·세션 허용·공식 deep link를 별도로 확인해야 합니다.',new if i>=7 else launch)
entry('pleos.navigation','Pleos 기본 내비',['좌표 목적지 안내','경로 취소'],'NaviHelper 3.0.5 연결 구현','목적지 이벤트와 좌표 일치로 확인. 차량 서비스·권한 필요.','https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations')
entry('local.audio','앱 자체 음원',['휴식 음원 반복 재생','중지'],'로컬 구현','프로젝트 생성 음원, 다른 앱의 재생을 중지하지 않습니다.','https://developer.android.com/media/media3/exoplayer/hello-world')
entry('google.calendar','Google Calendar',['앞으로 24시간 일정 읽기'],'서버 어댑터 · 계정 설정 필요','calendar.events.readonly OAuth 권한. 인증 등록·실제 호출은 아직 수행하지 않았습니다.','https://developers.google.com/workspace/calendar/api/v3/reference/events/list')
entry('home.assistant','Home Assistant',['선택한 조명 켜기·끄기','선택한 공조 목표 온도'],'서버 어댑터 · 허용 대상 설정 필요','관리자가 설정한 HTTPS 호스트·entity 허용 목록만 사용합니다. 응답 뒤 상태를 재조회합니다.','https://developers.home-assistant.io/docs/api/rest/')
entry('kakao.local','카카오 Local',['키워드 장소 검색'],'서버 어댑터 · 키 설정 필요','검색 결과를 선택해 좌표로 사용합니다. 카카오내비 차량 앱 직접 제어와 별개입니다.','https://developers.kakao.com/docs/latest/ko/local/dev-guide')
entry('what3words.api','what3words API',['세 단어 주소 → 좌표'],'서버 어댑터 · 별도 활성화 필요','요금·계정 제한 확인 후 키와 명시적 활성화가 필요합니다.','https://developer.what3words.com/public-api/docs')
entry('smartthings','SmartThings',['스마트홈 연결 후보'],'신규 등록 확인 필요','연동 앱 등록 정책·유료 플랜 조건 때문에 현재 자동 연결을 제공하지 않습니다.','https://developer.smartthings.com/docs/service-integrations/app-setup')
entry('radioline.api','Radioline API',['라디오·오디오 연동 후보'],'사업자 접근 신청 필요','공개 문서만으로 키·재생 권한을 발급할 수 없습니다.','https://business.radioline.co/')
entry('makemyday.api','Make My Day API',['경로 계획 후보'],'사업자 접근 신청 필요','API 제공사와 접근 계약 확인 필요.','https://www.makemydayapp.com/api.php')
write('connector-catalog.json',dict(version=1,checkedAt='2026-10-27',scope='확인한 공개 SDK·공식 입점 발표·공식 API. 전 세계 모든 서비스의 완전 목록은 아님.',entries=entries))
audio=ROOT/'android/app/src/main/assets/rest-tone.wav'
audio.parent.mkdir(parents=True,exist_ok=True)
rate=16000;seconds=8
with wave.open(str(audio),'wb') as f:
    f.setnchannels(1);f.setsampwidth(2);f.setframerate(rate)
    # Original quiet chord with a smooth envelope; no sampled third-party content.
    samples=(int(2600*(math.sin(2*math.pi*220*i/rate)+.4*math.sin(2*math.pi*330*i/rate))*math.sin(math.pi*i/(rate*seconds))**2) for i in range(rate*seconds))
    f.writeframes(b''.join(struct.pack('<h',s) for s in samples))
print(f'Generated strict contracts, {len(entries)} catalogue entries and original audio.')
