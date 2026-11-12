import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import "./ToastProvider.css";
const ToastContext=createContext(null);
const variants=new Set(["success","error","warning","info"]);
function safeMessage(value){
 const message=String(value??"").replace(/[\r\n\t]+/g," ").trim();
 if(!message)return "Something went wrong.";
 if(/\b(select|insert|update|delete|drop|alter)\s+.{1,80}\b(from|into|table|set)\b|\bat\s+.*\.(java|js):\d+|(?:[A-Z]:\\|\/)(?:[^\s]+\/)*[^\s]+\.(?:db|sqlite|java|js)|SQLITE_[A-Z_]+|SQLSTATE|org\.springframework|java\.lang\./i.test(message))return "The operation could not be completed. Check your data and try again.";
 return message.slice(0,240);
}
export function ToastProvider({children}){
 const [toasts,setToasts]=useState([]),timers=useRef(new Map()),recent=useRef(new Map());
 const dismiss=useCallback(id=>{const timer=timers.current.get(id);if(timer)clearTimeout(timer);timers.current.delete(id);setToasts(items=>items.filter(item=>item.id!==id));},[]);
 const notify=useCallback((variant,message,options={})=>{
  const text=safeMessage(message),duration=options.duration===0?0:Math.max(1500,Number(options.duration)||5000),key=options.key||variant+":"+text,now=Date.now(),last=recent.current.get(key);
  if(last&&now-last<900)return null;
  recent.current.set(key,now);for(const [k,t] of recent.current)if(now-t>3000)recent.current.delete(k);
  const id=globalThis.crypto?.randomUUID?.()||variant+"-"+now+"-"+Math.random().toString(36).slice(2);
  setToasts(items=>[...items.filter(item=>item.key!==key),{id,key,variant:variants.has(variant)?variant:"info",message:text,action:options.action&&typeof options.action.onClick==="function"&&typeof options.action.label==="string"?{label:options.action.label.slice(0,32),onClick:options.action.onClick}:null}].slice(-4));
  if(duration>0)timers.current.set(id,setTimeout(()=>dismiss(id),duration));
  return id;
 },[dismiss]);
 useEffect(()=>()=>{for(const timer of timers.current.values())clearTimeout(timer);},[]);
 const api={success:(m,o)=>notify("success",m,o),error:(m,o)=>notify("error",m,o),warning:(m,o)=>notify("warning",m,o),info:(m,o)=>notify("info",m,o),dismiss};
 return <ToastContext.Provider value={api}>{children}<div className="toast-viewport" aria-label="Notifications">{toasts.map(t=><div key={t.id} className="toast" data-variant={t.variant} role={t.variant==="error"?"alert":"status"} aria-live={t.variant==="error"?"assertive":"polite"}><span className="toast-message">{t.message}</span>{t.action&&<button type="button" className="toast-action" onClick={()=>{try{const result=t.action.onClick();if(result&&typeof result.catch==="function")result.catch(()=>{});}finally{dismiss(t.id);}}}>{t.action.label}</button>}<button type="button" className="toast-dismiss" aria-label="Dismiss notification" onClick={()=>dismiss(t.id)}>×</button></div>)}</div></ToastContext.Provider>;
}
export function useToast(){const value=useContext(ToastContext);if(!value)throw new Error("useToast must be used within ToastProvider");return value;}
