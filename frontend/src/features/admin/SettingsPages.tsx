import { currentTime } from "@/api/runtime";
import { PAGE_SIZE } from '@/components/ui/Pagination';
import { useState } from 'react';
import * as api from '@/api/generated/api';
import { Button } from '@/components/ui/button';
import { StatusBadge } from '@/components/ui/StatusBadge';
import { MenuPanel } from '@/components/layout/MenuPanel';
import { Dialog } from '@/components/ui/Dialog';
import { AdminPage, AdminLinks, AdminForm, DataTable, QueryState, Filters, Facts, ActionDialog, PendingFeature, ALL, money, filterValue, textValue, numValue, type Values, type Action, type FormField } from './AdminUI';
import type { Navigate } from './MemberPages';
export function SettingsPage({onNavigate}:{onNavigate:Navigate}) {
  const products = api.useGetPassProducts({page:1,size:3});
  const types = api.useGetLessonTypes({page:1,size:3});
  const rooms = api.useGetRooms({page:1,size:3});
  return <AdminPage title="스튜디오 설정" description="예약 규칙과 운영시간을 설정하고, 상품·수업 유형·공간을 관리합니다.">
    <section className="admin-setting-section" aria-labelledby="reservation-settings-heading">
      <header><h2 id="reservation-settings-heading">예약 규칙</h2><p>회원의 취소 마감과 미수 이용권 예약 기준을 설정합니다.</p></header>
      <ReservationPolicySettings />
    </section>
    <section className="admin-setting-section" aria-labelledby="hours-settings-heading">
      <header><h2 id="hours-settings-heading">영업시간·정기 휴무</h2><p>요일별 운영시간을 서울 시각으로 설정합니다. 기존 수업은 자동으로 변경되지 않습니다.</p></header>
      <BusinessHoursSettings />
    </section>
    <section className="admin-setting-section" aria-labelledby="catalog-settings-heading">
      <header><h2 id="catalog-settings-heading">상품·수업·공간</h2><p>회원에게 발급할 상품과 수업 운영에 사용하는 기준 정보를 관리합니다.</p></header>
      <MenuPanel className="admin-setting-catalog">
        {([{title:'이용권 상품',description:'가격·총 횟수·유효 기간',screen:'admin-products',query:products},
          {title:'수업 유형',description:'수업 이름·기본 정원·활성 상태',screen:'admin-types',query:types},
          {title:'룸',description:'공간 이름·수용 인원',screen:'admin-rooms',query:rooms}] as const).map(item =>
          <div className="admin-setting-row" key={item.screen}>
            <div><h3>{item.title}</h3><p className="menu-muted">{item.description}</p>
              <QueryState query={item.query}><p className="menu-muted">{item.query.data?.content.map(row=>row.name).join(' · ') || '등록된 항목이 없습니다'}{(item.query.data?.pageInfo.totalElements ?? 0)>3?' 외':''}</p></QueryState>
            </div>
            <Button variant="outline" onClick={()=>onNavigate(item.screen)}>{item.title} 관리</Button>
          </div>)}
      </MenuPanel>
    </section>
  </AdminPage>;
}
const productFields:FormField[]=[{name:'name',label:'상품명',required:true,maxLength:100},{name:'price',label:'가격 (원)',type:'number',required:true,min:0},{name:'totalCount',label:'총 횟수',type:'number',required:true,min:1,max:1000},{name:'validityDays',label:'유효 기간 (일)',type:'number',required:true,min:1,max:3650},{name:'description',label:'설명',type:'textarea',maxLength:500}];
export function ProductsPage({onNavigate}:{onNavigate:Navigate}) {
  const [page,setPage]=useState(1),[filters,setFilters]=useState<Values>({}),[action,setAction]=useState<Action|null>(null),[selected,setSelected]=useState<api.PassProduct|null>(null),[pending,setPending]=useState<{id:number;data:api.PassProductUpdateRequest}|null>(null);
  const query=api.useGetPassProducts({keyword:filterValue(filters,'keyword'),page,size:PAGE_SIZE}),create=api.useCreatePassProduct(),update=api.useUpdatePassProduct(),remove=api.useDeletePassProduct();
  const edit=(p?:api.PassProduct)=>setAction({title:p?'상품 수정':'상품 등록',description:p?'기본적으로 이후 발급분에 적용됩니다. 기존 발급 금액은 유지됩니다.':'발급 시 적용할 기본 가격과 횟수·기간을 입력합니다.',submitLabel:p?'상품 변경 확인':'상품 등록',fields:[...productFields,...(p?[{name:'applyToIssued',label:'기존 발급 이용권에도 횟수·기간 반영',type:'checkbox' as const,description:'사용 중·시작 전 이용권만 대상입니다. 개별 만료일 조정은 덮어쓸 수 있습니다.'}]:[])],initial:p?{name:p.name,price:String(p.price),totalCount:String(p.totalCount),validityDays:String(p.validityDays),description:p.description??'',applyToIssued:false}:{},onSubmit:async v=>{const data={name:textValue(v,'name').trim(),price:numValue(v,'price'),totalCount:numValue(v,'totalCount'),validityDays:numValue(v,'validityDays'),description:textValue(v,'description')};if(p&&v.applyToIssued){setPending({id:p.id,data:{...data,applyToIssued:true}});return;}return p?update.mutateAsync({passProductId:p.id,data}):create.mutateAsync({data});}});
  return <AdminPage title="이용권 상품" description="회원에게 발급할 상품의 기준 정보를 관리합니다." action={<Button variant="ghost" onClick={()=>onNavigate('admin-settings')}>스튜디오 설정으로</Button>}><Filters fields={[{name:'keyword',label:'상품명 검색'}]} action={<Button onClick={()=>edit()}>상품 등록</Button>} onApply={v=>{setFilters(v);setPage(1);}}/><QueryState query={query}><DataTable rows={query.data?.content??[]} pageInfo={query.data?.pageInfo} onPage={setPage} columns={[{label:'상품',render:p=><strong>{p.name}</strong>},{label:'가격',money:true,render:p=>money(p.price)},{label:'횟수',render:p=>`${p.totalCount}회`},{label:'유효 기간',render:p=>`${p.validityDays}일`},{label:'관리',render:p=><div className="menu-actions"><Button variant="ghost" onClick={()=>setSelected(p)}>상세 보기</Button><Button variant="outline" onClick={()=>edit(p)}>수정</Button><Button variant="destructive" onClick={()=>setAction({title:'상품 삭제',description:'발급 이력이 없는 상품만 삭제할 수 있습니다.',submitLabel:'상품 삭제',submitVariant:'destructive',danger:true,onSubmit:()=>remove.mutateAsync({passProductId:p.id})})}>삭제</Button></div>}]}/></QueryState><PendingFeature title="판매 중지·재개" description="판매 상태를 관리하는 운영 기준이 확정되면 제공됩니다. 발급 이력이 있는 상품은 삭제할 수 없습니다."/>{selected&&<Dialog dialog={{title:'상품 상세',body:selected.name}} onClose={()=>setSelected(null)}><Facts items={[["상품",selected.name],["가격",money(selected.price)],["총 횟수",`${selected.totalCount}회`],["유효 기간",`${selected.validityDays}일`],["설명",selected.description]]}/></Dialog>}{action&&<ActionDialog action={action} onClose={()=>setAction(null)}/>} {pending&&!action&&<ProductImpact pending={pending} onClose={()=>setPending(null)}/>}</AdminPage>;
}
function ProductImpact({pending,onClose}:{pending:{id:number;data:api.PassProductUpdateRequest};onClose:()=>void}) {
  const active=api.useGetPasses({passProductId:pending.id,status:'ACTIVE',size:100}),upcoming=api.useGetPasses({passProductId:pending.id,status:'UPCOMING',size:100}),product=api.useGetPassProduct(pending.id),update=api.useUpdatePassProduct();
  const total=(active.data?.pageInfo.totalElements??0)+(upcoming.data?.pageInfo.totalElements??0);
  const ready=!!active.data&&!!upcoming.data&&!!product.data;
  return <ActionDialog onClose={onClose} action={{title:'기존 발급 이용권 변경 확인',description:`사용 중·시작 전 이용권 ${ready?total:'조회 중'}건의 횟수와 만료일이 변경됩니다. 홀딩 중·만료 이용권과 발급 가격은 유지됩니다.`,submitLabel:'기존 발급에 반영',danger:true,content:<QueryState query={active}><QueryState query={upcoming}><QueryState query={product}><Facts items={[["총 횟수",`${product.data?.totalCount??'—'} → ${pending.data.totalCount}회`],["만료일",`각 이용권 시작일 + ${Number(pending.data.validityDays)-1}일로 재설정`],["잔여 횟수",`횟수 차이만큼 증감 (최소 0회)`]]}/></QueryState></QueryState></QueryState>,fields:[{name:'confirmed',label:'개별 만료일 조정이 덮어써지는 것을 확인했습니다',type:'checkbox'}],onSubmit:async v=>{if(!ready)throw new Error('영향 대상을 먼저 조회해 주세요.');if(!v.confirmed)throw new Error('변경 영향을 확인해 주세요.');return update.mutateAsync({passProductId:pending.id,data:pending.data});}}}/>;
}
export function LessonTypesPage({onNavigate}:{onNavigate:Navigate}) {
  const [page,setPage]=useState(1),[filters,setFilters]=useState<Values>({}),[action,setAction]=useState<Action|null>(null);
  const query=api.useGetLessonTypes({active:filterValue(filters,'active')?filters.active==='true':undefined,page,size:PAGE_SIZE}),create=api.useCreateLessonType(),update=api.useUpdateLessonType();
  const edit=(t?:api.LessonType)=>setAction({title:t?'수업 유형 수정':'수업 유형 등록',description:'정원은 새로 개설하는 수업부터 적용되며 기존 수업은 유지됩니다.',submitLabel:t?'유형 저장':'유형 등록',fields:[...(!t?[{name:'code',label:'유형 코드',required:true,pattern:'[A-Z][A-Z0-9_]{1,29}',description:'영문 대문자로 시작하는 대문자·숫자·밑줄 2~30자'}]:[]),{name:'name',label:'유형 이름',required:true,maxLength:50},{name:'capacity',label:'정원',type:'number',required:true,min:1,max:100}],initial:t?{name:t.name,capacity:String(t.capacity)}:{},onSubmit:v=>t?update.mutateAsync({lessonTypeId:t.id,data:{name:textValue(v,'name').trim(),capacity:numValue(v,'capacity')}}):create.mutateAsync({data:{code:textValue(v,'code'),name:textValue(v,'name').trim(),capacity:numValue(v,'capacity')}})});
  return <AdminPage title="수업 유형" description="수업 정원과 활성 상태, 권한에 사용하는 유형을 관리합니다." action={<Button variant="ghost" onClick={()=>onNavigate('admin-settings')}>스튜디오 설정으로</Button>}><Filters fields={[{name:'active',label:'상태',type:'select',options:[ALL,{value:'true',label:'활성'},{value:'false',label:'비활성'}]}]} action={<Button onClick={()=>edit()}>유형 등록</Button>} onApply={v=>{setFilters(v);setPage(1);}}/><QueryState query={query}><DataTable rows={query.data?.content??[]} pageInfo={query.data?.pageInfo} onPage={setPage} columns={[{label:'유형',render:t=>t.name},{label:'코드',render:t=>t.code},{label:'정원',render:t=>`${t.capacity}명`},{label:'상태',render:t=><StatusBadge domain="setting" status={t.active?'ACTIVE':'INACTIVE'}/>},{label:'관리',render:t=><div className="menu-actions"><Button variant="outline" onClick={()=>edit(t)}>수정</Button><Button variant={t.active?'destructive':'outline'} onClick={()=>setAction({title:'유형 상태 변경',description:t.active?'새 수업 개설을 제한합니다. 기존 수업과 부여된 권한은 유지됩니다.':'새 수업 개설에 사용할 수 있도록 활성화합니다.',submitLabel:t.active?'비활성화':'활성화',danger:true,onSubmit:()=>update.mutateAsync({lessonTypeId:t.id,data:{active:!t.active}})})}>{t.active?'비활성화':'활성화'}</Button></div>}]}/></QueryState>{action&&<ActionDialog action={action} onClose={()=>setAction(null)}/>}</AdminPage>;
}
export function RoomsPage({onNavigate}:{onNavigate:Navigate}) {
  const [page,setPage]=useState(1),[action,setAction]=useState<Action|null>(null),[selected,setSelected]=useState<number|null>(null);
  const query=api.useGetRooms({page,size:PAGE_SIZE}),create=api.useCreateRoom(),update=api.useUpdateRoom(),remove=api.useDeleteRoom();
  const edit=(r?:api.Room)=>setAction({title:r?'룸 수정':'룸 등록',description:'룸 수용 인원을 변경해도 기존 수업의 정원은 유지됩니다.',submitLabel:r?'룸 저장':'룸 등록',fields:[{name:'name',label:'룸 이름',required:true,maxLength:50},{name:'capacity',label:'수용 인원',type:'number',required:true,min:1,max:100}],initial:r?{name:r.name,capacity:String(r.capacity)}:{},onSubmit:v=>r?update.mutateAsync({roomId:r.id,data:{name:textValue(v,'name').trim(),capacity:numValue(v,'capacity')}}):create.mutateAsync({data:{name:textValue(v,'name').trim(),capacity:numValue(v,'capacity')}})});
  return <AdminPage title="룸" description="센터의 수업 공간과 수용 인원을 관리합니다." action={<Button variant="ghost" onClick={()=>onNavigate('admin-settings')}>스튜디오 설정으로</Button>}><QueryState query={query}><DataTable rows={query.data?.content??[]} pageInfo={query.data?.pageInfo} onPage={setPage} action={<Button onClick={()=>edit()}>룸 등록</Button>} columns={[{label:'룸',render:r=>r.name},{label:'수용 인원',render:r=>`${r.capacity}명`},{label:'관리',render:r=><div className="menu-actions"><Button variant="ghost" onClick={()=>setSelected(r.id)}>상세·사용 예정 수업</Button><Button variant="outline" onClick={()=>edit(r)}>수정</Button><Button variant="destructive" onClick={()=>setAction({title:'룸 삭제',description:'시작 전 수업이 있으면 삭제할 수 없습니다. 기존 수업의 공간을 먼저 조정해 주세요.',submitLabel:'룸 삭제',submitVariant:'destructive',danger:true,onSubmit:()=>remove.mutateAsync({roomId:r.id})})}>삭제</Button></div>}]}/></QueryState><PendingFeature title="룸 비활성" description="룸의 사용 중지 기준이 확정되면 제공됩니다. 현재는 룸 수정과 사용 예정 수업이 없는 룸의 삭제를 지원합니다."/>{selected&&<RoomDetail id={selected} onClose={()=>setSelected(null)} onNavigate={onNavigate}/>} {action&&<ActionDialog action={action} onClose={()=>setAction(null)}/>}</AdminPage>;
}
function RoomDetail({id,onClose,onNavigate}:{id:number;onClose:()=>void;onNavigate:Navigate}) {
  const query=api.useGetRoom(id),[page,setPage]=useState(1),lessons=api.useGetLessons({roomId:id,from:currentTime(),page,size:PAGE_SIZE});
  return <Dialog dialog={{title:'룸 상세',body:'룸 정보와 사용 예정 수업'}} onClose={onClose}><div className="menu-stack"><QueryState query={query}>{query.data&&<Facts items={[["룸",query.data.name],["수용 인원",`${query.data.capacity}명`]]}/>}</QueryState><QueryState query={lessons}><DataTable rows={(lessons.data?.content??[]).filter(l=>l.status!=='CANCELED')} pageInfo={lessons.data?.pageInfo} onPage={setPage} columns={[{label:'수업',render:l=>l.title},{label:'상세',render:l=><Button variant="ghost" onClick={()=>{onClose();onNavigate('admin-lesson-detail',l.id);}}>수업 보기</Button>}]}/></QueryState></div></Dialog>;
}
export function HoursPage({onNavigate}:{onNavigate:Navigate}) {
  return <AdminPage title="영업시간·휴무일" description="요일별 영업시간은 서울 시각을 기준으로 설정합니다." action={<Button variant="ghost" onClick={()=>onNavigate('admin-settings')}>스튜디오 설정으로</Button>}><BusinessHoursSettings /><PendingFeature title="날짜별 휴무·이용권 일괄 보상" description="휴무 기간별 대상 선정과 중복 보상 기준이 확정되면 제공됩니다. 요일별 정기 휴무는 위에서 설정할 수 있습니다."/></AdminPage>;
}
function BusinessHoursSettings() {
  const query=api.useGetBusinessHours(),save=api.useUpdateBusinessHours(),[action,setAction]=useState<Action|null>(null);
  const days=['MON','TUE','WED','THU','FRI','SAT','SUN'] as const,labels=['월','화','수','목','금','토','일'];
  return <><QueryState query={query}>{query.data&&<MenuPanel className="admin-hours-form"><AdminForm key={JSON.stringify(query.data)} fields={days.flatMap((day,i)=>[{name:`${day}-closed`,label:`${labels[i]}요일 정기 휴무`,type:'checkbox' as const},{name:`${day}-open`,label:`${labels[i]}요일 오픈`,type:'time' as const},{name:`${day}-close`,label:`${labels[i]}요일 마감`,type:'time' as const}])} initial={Object.fromEntries(query.data.businessHours.flatMap(h=>[[`${h.dayOfWeek}-closed`,h.closed],[`${h.dayOfWeek}-open`,h.openTime??'09:00'],[`${h.dayOfWeek}-close`,h.closeTime??'21:00']]))} submitLabel="영업시간 변경 확인" onSubmit={async v=>{const businessHours=days.map(day=>({dayOfWeek:day,closed:v[`${day}-closed`]===true,openTime:v[`${day}-closed`]?null:textValue(v,`${day}-open`),closeTime:v[`${day}-closed`]?null:textValue(v,`${day}-close`)}));if(businessHours.some(h=>!h.closed&&(!h.openTime||!h.closeTime||h.openTime>=h.closeTime)))throw new Error('영업일의 마감은 오픈 이후여야 합니다.');setAction({title:'영업시간 변경',description:'이후 수업 개설에 적용합니다. 기존 수업을 자동 변경하거나 취소하지 않습니다.',submitLabel:'영업시간 저장',danger:true,content:<Facts items={businessHours.map((h,i)=>[`${labels[i]}요일`,h.closed?'휴무':`${h.openTime} ~ ${h.closeTime}`])}/>,onSubmit:()=>save.mutateAsync({data:{businessHours}})});}}/></MenuPanel>}</QueryState>{action&&<ActionDialog action={action} onClose={()=>setAction(null)}/>}</>;
}
export function PolicyPage({onNavigate}:{onNavigate:Navigate}) {
  return <AdminPage title="예약 정책" description="회원 예약 취소 마감과 미수 이용권 예약 기준을 설정합니다." action={<Button variant="ghost" onClick={()=>onNavigate('admin-settings')}>스튜디오 설정으로</Button>}><ReservationPolicySettings /></AdminPage>;
}
function ReservationPolicySettings() {
  const query=api.useGetReservationPolicy(),save=api.useUpdateReservationPolicy(),[action,setAction]=useState<Action|null>(null);
  return <><QueryState query={query}>{query.data&&<MenuPanel className="admin-full-form"><AdminForm key={JSON.stringify(query.data)} fields={[{name:'cancelDeadlineHours',label:'취소 마감 (수업 시작 전 시간)',type:'number',required:true,min:0,max:720,description:'0시간은 수업 시작 직전까지 취소할 수 있습니다.'},{name:'allowUnpaidReservation',label:'미수 이용권 예약 허용',type:'checkbox'}]} initial={{cancelDeadlineHours:String(query.data.cancelDeadlineHours),allowUnpaidReservation:query.data.allowUnpaidReservation}} submitLabel="정책 변경 확인" onSubmit={async v=>{const data={cancelDeadlineHours:numValue(v,'cancelDeadlineHours'),allowUnpaidReservation:v.allowUnpaidReservation===true};setAction({title:'예약 정책 변경',description:'취소 마감시간은 기존 예약의 이후 취소 요청에도 적용됩니다. 미수 허용은 신규 예약부터 적용하며 기존 예약은 유지됩니다.',submitLabel:'정책 저장',danger:true,content:<Facts items={[["취소 마감",`${query.data!.cancelDeadlineHours} → ${data.cancelDeadlineHours}시간`],["미수 예약",`${query.data!.allowUnpaidReservation?'허용':'불가'} → ${data.allowUnpaidReservation?'허용':'불가'}`]]}/>,onSubmit:()=>save.mutateAsync({data})});}}/><p className="menu-note">관리자는 회원 취소 마감시간과 관계없이 시작 전 예약을 취소할 수 있습니다.</p></MenuPanel>}</QueryState>{action&&<ActionDialog action={action} onClose={()=>setAction(null)}/>}</>;
}
