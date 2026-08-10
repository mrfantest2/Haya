import 'dotenv/config';
import express from 'express';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import cron from 'node-cron';
import { ImapFlow } from 'imapflow';
import { simpleParser } from 'mailparser';
import nodemailer from 'nodemailer';
import * as cheerio from 'cheerio';
import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import fssync from 'node:fs';
import path from 'node:path';

const ROOT='/app';
const DATA=path.join(ROOT,'data');
const JOBS=path.join(DATA,'jobs.json');
const STATE=path.join(DATA,'state.json');
const APPS=path.join(ROOT,'applications');
const PROFILE=path.join(ROOT,'config','haya_profile.json');
const LOGS=path.join(ROOT,'logs');

const cfg={
  port:Number(process.env.PORT||8787),
  email:process.env.HAYA_EMAIL||'saadehhaya@yahoo.com',
  password:process.env.MAIL_APP_PASSWORD||'',
  imapHost:process.env.IMAP_HOST||'imap.mail.yahoo.com',
  imapPort:Number(process.env.IMAP_PORT||993),
  smtpHost:process.env.SMTP_HOST||'smtp.mail.yahoo.com',
  smtpPort:Number(process.env.SMTP_PORT||465),
  schedule:process.env.SCAN_SCHEDULE||'0 */6 * * *',
  minScore:Number(process.env.MIN_MATCH_SCORE||80),
  maxRisk:Number(process.env.MAX_FRAUD_RISK||30),
  maxHours:Number(process.env.MAX_WEEKLY_HOURS||30),
  dailyLimit:Number(process.env.DAILY_APPLICATION_LIMIT||5),
  autoApply:String(process.env.AUTO_EMAIL_APPLY||'false').toLowerCase()==='true',
  workAuth:(process.env.WORK_AUTHORIZATION_HUNGARY||'').trim(),
  sponsorship:(process.env.SPONSORSHIP_REQUIRED||'').trim(),
  startDate:(process.env.EARLIEST_START_DATE||'').trim(),
  cv:process.env.CV_PATH||'/app/documents/Haya_Saadeh_CV.pdf'
};

const app=express();
app.disable('x-powered-by');
app.use(helmet({contentSecurityPolicy:false}));
app.use(rateLimit({windowMs:60_000,limit:120}));
app.use(express.json({limit:'64kb'}));
app.use(express.static(path.join(ROOT,'public')));

const clean=s=>String(s??'').replace(/\s+/g,' ').trim();
const readJSON=async(p,f)=>{try{return JSON.parse(await fs.readFile(p,'utf8'))}catch{return f}};
const writeJSON=async(p,v)=>{const t=p+'.tmp';await fs.writeFile(t,JSON.stringify(v,null,2));await fs.rename(t,p)};
const log=async(message,meta={})=>{await fs.mkdir(LOGS,{recursive:true});await fs.appendFile(path.join(LOGS,'agent.log'),JSON.stringify({time:new Date().toISOString(),message,...meta})+'\n')};

async function ensure(){
  for(const d of [DATA,APPS,LOGS]) await fs.mkdir(d,{recursive:true});
  if(!fssync.existsSync(JOBS)) await writeJSON(JOBS,[]);
  if(!fssync.existsSync(STATE)) await writeJSON(STATE,{seen:[],lastRun:null,lastError:null,applicationsToday:{}});
}

function linksFrom(html,text){
  const out=new Set();
  try{const $=cheerio.load(html||'');$('a[href]').each((_,e)=>{const h=$(e).attr('href')||'';if(/^https?:\/\//i.test(h))out.add(h)})}catch{}
  for(const m of String(text||'').match(/https?:\/\/[^\s<>"')\]]+/gi)||[]) out.add(m.replace(/[.,;!?]+$/,''));
  return [...out].filter(u=>!/unsubscribe|privacy|preferences|tracking|facebook|instagram|youtube/i.test(u)).slice(0,16);
}

async function fetchPage(url){
  const c=new AbortController();const t=setTimeout(()=>c.abort(),12000);
  try{
    const r=await fetch(url,{redirect:'follow',signal:c.signal,headers:{'user-agent':'Mozilla/5.0 HayaJobAutopilot/2.0'}});
    if(!r.ok)throw new Error('HTTP '+r.status);
    const html=(await r.text()).slice(0,2_000_000);const $=cheerio.load(html);$('script,style,noscript,svg,iframe').remove();
    return {url:r.url,title:clean($('title').first().text()),text:clean($('body').text()).slice(0,100_000),html};
  } finally {clearTimeout(t)}
}

function scoreJob(text,profile){
  const x=text.toLowerCase();let score=0;const why=[];const concerns=[];
  const titles=profile.priority_roles||[];if(titles.some(t=>x.includes(t.toLowerCase()))){score+=25;why.push('Target role match')}
  const keys=['training','recruit','onboard','operations','coordinator','programme','program','report','schedule','stakeholder','aviation','travel','patient','customer'];
  const hits=keys.filter(k=>x.includes(k)).length;score+=Math.min(20,hits*2);if(hits>=5)why.push('Strong experience overlap');
  if(/\benglish\b|english-speaking|fluent english/i.test(text)){score+=15;why.push('English-language role')}else concerns.push('English requirement unclear');
  if(/budapest|hybrid.*hungary|hungary.*hybrid/i.test(text)){score+=10;why.push('Budapest / Hungary hybrid')}else concerns.push('Budapest location unclear');
  if(/part[- ]time|student job|intern(ship)?|részmunkaidő|gyakornok|20 hours|25 hours|30 hours/i.test(text)){score+=10;why.push('Student-compatible schedule')}else concerns.push('Weekly hours unclear');
  if(/coordina|administration|schedule|reporting|documentation|stakeholder/i.test(text)){score+=10;why.push('Coordination/administration duties')}
  if(/arabic/i.test(text)){score+=5;why.push('Arabic advantage')}
  if(/hungarian.*(?:b2|c1|c2|fluent|native)|native hungarian/i.test(text)){score-=45;concerns.push('Advanced Hungarian appears mandatory')}
  if(/unpaid|commission[- ]only|application fee|processing fee|pay.*training/i.test(text)){score-=70;concerns.push('Unsafe or unsuitable compensation/application term')}
  return {score:Math.max(0,Math.min(100,score)),why,concerns};
}

function risk(text){
  let n=0;const signals=[];const add=(v,s)=>{n+=v;signals.push(s)};
  if(/application fee|processing fee|send money|pay.*before/i.test(text))add(70,'Applicant payment request');
  if(/crypto|bitcoin|gift card|western union|moneygram/i.test(text))add(65,'Unusual payment method');
  if(/bank account|online banking|credit card/i.test(text))add(40,'Banking information request');
  if(/telegram|whatsapp only/i.test(text))add(20,'Messaging-app-only recruitment');
  if(/no interview|guaranteed job|start today/i.test(text))add(25,'Unrealistic hiring process');
  return {risk:Math.min(100,n),signals};
}

function emails(text){return [...new Set((String(text).match(/[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}/gi)||[]).map(x=>x.toLowerCase()))]}
function corporate(email,url){try{const d=email.split('@')[1];const h=new URL(url).hostname.replace(/^www\./,'');return !!d&&!['gmail.com','yahoo.com','outlook.com','hotmail.com','icloud.com'].includes(d)&&(h===d||h.endsWith('.'+d)||d.endsWith('.'+h))}catch{return false}}
function idFor(url,title){return crypto.createHash('sha256').update(url+'|'+title).digest('hex').slice(0,20)};

function letter(job,p){return `Dear Hiring Team,\n\nI am applying for the ${job.title} opportunity. I am currently completing a full-time MBA at Corvinus University of Budapest and bring more than seven years of experience across aviation, travel, healthcare, recruitment coordination, training administration, onboarding, reporting and multi-stakeholder operations.\n\nI am based in Budapest, speak Arabic natively and English at C1 level, and I am available for student-compatible on-site or hybrid work. I would welcome the opportunity to discuss how my operational experience and MBA studies can support your team.\n\nKind regards,\n${p.name}\n${p.phone}\n${p.email}\n${p.linkedin}`}

async function prepare(job,p){const file=path.join(APPS,job.id+'.txt');await fs.writeFile(file,`JOB: ${job.title}\nURL: ${job.url}\nMATCH: ${job.score}%\nRISK: ${job.risk}/100\nSTATUS: ${job.status}\n\nWHY\n${job.why.map(x=>'- '+x).join('\n')}\n\nCHECKS\n${job.concerns.map(x=>'- '+x).join('\n')||'- No material concerns detected'}\n\nAPPLICATION\n${letter(job,p)}\n`);return file}

async function send(job,p){
  const tr=nodemailer.createTransport({host:cfg.smtpHost,port:cfg.smtpPort,secure:true,auth:{user:cfg.email,pass:cfg.password}});
  return tr.sendMail({from:{name:p.name,address:cfg.email},to:job.applicationEmail,subject:`Application - ${job.title} - ${p.name}`,text:letter(job,p),attachments:[{filename:'Haya_Saadeh_CV.pdf',path:cfg.cv}]});
}

function autoReady(job){const blocks=[];if(!cfg.autoApply)blocks.push('Auto-apply disabled');if(!cfg.password)blocks.push('Yahoo app password missing');if(!cfg.workAuth)blocks.push('Work authorization missing');if(!cfg.sponsorship)blocks.push('Sponsorship answer missing');if(!cfg.startDate)blocks.push('Start date missing');if(!fssync.existsSync(cfg.cv))blocks.push('CV missing');if(job.score<85)blocks.push('Match below 85%');if(job.risk>cfg.maxRisk)blocks.push('Risk above limit');if(!job.applicationEmail||!corporate(job.applicationEmail,job.url))blocks.push('No verified corporate application email');return blocks}

let running=false;
async function run(reason='scheduled'){
  if(running)return {ok:false,error:'Already running'};running=true;await ensure();
  const state=await readJSON(STATE,{seen:[],applicationsToday:{}});const jobs=await readJSON(JOBS,[]);const profile=await readJSON(PROFILE,{});const added=[];
  try{
    if(!cfg.password)throw new Error('Yahoo app password is not configured');
    const imap=new ImapFlow({host:cfg.imapHost,port:cfg.imapPort,secure:true,auth:{user:cfg.email,pass:cfg.password},logger:false});
    await imap.connect();const lock=await imap.getMailboxLock('INBOX');
    try{
      const ids=await imap.search({since:new Date(Date.now()-7*86400000)});
      for await(const m of imap.fetch(ids.slice(-80),{uid:true,source:true,envelope:true})){
        const mid=String(m.envelope?.messageId||m.uid);if(state.seen.includes(mid))continue;
        const parsed=await simpleParser(m.source);const text=clean(parsed.text||'');for(const url of linksFrom(parsed.html||'',text)){
          if(jobs.some(j=>j.url===url)||added.some(j=>j.url===url))continue;
          try{
            const page=await fetchPage(url);const content=clean(`${parsed.subject||''} ${page.title} ${page.text}`);const s=scoreJob(content,profile);const r=risk(content);const title=page.title.split(/\s[-|–—]\s/)[0].slice(0,180)||parsed.subject||'Opportunity';const mail=emails(content).find(e=>corporate(e,page.url))||'';
            const job={id:idFor(page.url,title),title,url:page.url,score:s.score,risk:r.risk,why:s.why,concerns:s.concerns,riskSignals:r.signals,applicationEmail:mail,status:'Prepared - review required',foundAt:new Date().toISOString()};job.autoSubmitBlocks=autoReady(job);await prepare(job,profile);
            if(job.autoSubmitBlocks.length===0){const day=new Intl.DateTimeFormat('en-CA',{timeZone:'Europe/Budapest'}).format(new Date());const count=Number(state.applicationsToday?.[day]||0);if(count<cfg.dailyLimit){const info=await send(job,profile);job.status='Applied automatically by verified email';job.smtpMessageId=info.messageId;state.applicationsToday[day]=count+1}else job.status='Queued - daily limit reached'}
            added.push(job);
          }catch(e){await log('Vacancy processing failed',{url,error:e.message})}
        }
        state.seen.push(mid);
      }
    } finally {lock.release();await imap.logout()}
    state.seen=state.seen.slice(-1000);state.lastRun=new Date().toISOString();state.lastError=null;await writeJSON(JOBS,[...added,...jobs].slice(0,2000));await writeJSON(STATE,state);await log('Run complete',{reason,newJobs:added.length});return {ok:true,newJobs:added.length};
  }catch(e){state.lastRun=new Date().toISOString();state.lastError=e.message;await writeJSON(STATE,state);await log('Run failed',{reason,error:e.message});return {ok:false,error:e.message}}
  finally{running=false}
}

app.get('/api/status',async(_q,r)=>{await ensure();const s=await readJSON(STATE,{}),j=await readJSON(JOBS,[]);r.json({ok:true,running,lastRun:s.lastRun||null,lastError:s.lastError||null,jobs:j.length,prepared:j.filter(x=>x.status.startsWith('Prepared')).length,applied:j.filter(x=>x.status.includes('Applied')).length,autoEmailApply:cfg.autoApply,configurationComplete:Boolean(cfg.password&&cfg.workAuth&&cfg.sponsorship&&cfg.startDate&&fssync.existsSync(cfg.cv))})});
app.get('/api/jobs',async(_q,r)=>{await ensure();r.json({ok:true,jobs:await readJSON(JOBS,[])})});
app.post('/api/run',async(_q,r)=>{const x=await run('manual');r.status(x.ok?200:409).json(x)});
app.get('/api/application/:id',async(q,r)=>{const f=path.join(APPS,q.params.id+'.txt');if(!fssync.existsSync(f))return r.status(404).send('Not found');r.type('text/plain').send(await fs.readFile(f,'utf8'))});
app.get('/healthz',(_q,r)=>r.json({ok:true}));
await ensure();
cron.schedule(cron.validate(cfg.schedule)?cfg.schedule:'0 */6 * * *',()=>run('cron'),{timezone:'Europe/Budapest'});
app.listen(cfg.port,'0.0.0.0',()=>log('Agent started',{port:cfg.port,schedule:cfg.schedule}));
