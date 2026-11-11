const allowed=new Set(["VSCODE","NETBEANS"]),kinds=new Set([...allowed,"BROWSER","OTHER","UNKNOWN"]);
// Focus events alone cannot identify applications. Only fresh local bridge samples grant an IDE exception.
export class IdeFocusMonitor {
  constructor({active,focused,onSample,onFailure,request=(...args)=>globalThis.fetch(...args)}){Object.assign(this,{active,focused,onSample,onFailure,request});this.connected=false;this.running=false;this.cursor=0;this.boot="";this.generation=0;this.timer=null;this.pending=null;this.focusAt=Date.now();}
  reset(){++this.generation;this.running=false;this.connected=false;this.cursor=0;this.boot="";this.pending=null;clearTimeout(this.timer);}
  async connect(){this.reset();this.running=true;try{await this.sample();}catch(error){this.reset();throw error;}this.schedule();}
  schedule(){clearTimeout(this.timer);if(this.running)this.timer=setTimeout(async()=>{try{if(this.active())await this.sample();}catch(_){}finally{this.schedule();}},650);}
  noteFocus(){this.focusAt=Date.now();}
  async sample(){
    if(this.pending)return this.pending;
    const generation=this.generation;
    this.pending=(async()=>{
      try{
        const response=await this.request(`http://127.0.0.1:18765/status?since=${this.cursor}`,{method:"GET",mode:"cors",cache:"no-store",credentials:"omit",signal:AbortSignal.timeout(1400)});
        if(!response.ok)throw Error("IDE bridge unavailable");const data=await response.json();
        if(generation!==this.generation)return;
        if(data.protocol!==1||!kinds.has(data.kind)||typeof data.boot!=="string"||!Number.isSafeInteger(data.sequence)||data.sequence<0||!Number.isSafeInteger(data.sampledAt)||Math.abs(Date.now()-data.sampledAt)>4000||!Array.isArray(data.changes)||data.changes.length>600||!data.changes.every(c=>Number.isSafeInteger(c.sequence)&&c.sequence>0&&c.sequence<=data.sequence&&Number.isSafeInteger(c.at)&&kinds.has(c.kind))||data.truncated)throw Error("Invalid or stale IDE bridge sample");
        const sameBoot=this.boot===data.boot;
        if(this.boot&&(!sameBoot||data.sequence<this.cursor))throw Error("IDE bridge restarted or sequence regressed");
        const focused=this.focused();
        const unapproved=sameBoot&&data.changes.some(c=>Number.isSafeInteger(c.sequence)&&c.sequence>this.cursor&&!allowed.has(c.kind)&&(!focused||c.at<this.focusAt-350));
        this.boot=data.boot;this.cursor=data.sequence;this.connected=true;
        this.onSample({allowed:allowed.has(data.kind),kind:data.kind,unapproved,focused});
      }catch(error){if(generation===this.generation){this.connected=false;this.onFailure();}throw error;}
      finally{if(generation===this.generation)this.pending=null;}
    })();return this.pending;
  }
}