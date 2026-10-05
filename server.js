import express from "express";
import path from "path";
import { fileURLToPath } from "url";
import multer from "multer";

const app=express();
const __dirname=path.dirname(fileURLToPath(import.meta.url));
const PORT=process.env.PORT||3000;
app.use(express.json({limit:"1mb"}));
app.use(express.static(path.join(__dirname,"public")));
const upload=multer({storage:multer.memoryStorage(),limits:{fileSize:25*1024*1024}});

const SYSTEM="אתה עוזר אישי חכם בעברית וגם סוכן Android. אפשר לדבר איתך בחופשיות, לשאול שאלות, לבקש הסברים, עצות, ידע, חישובים או סתם לנהל שיחה — ובמקרים כאלה ענה תשובה טבעית וברורה ואל תפעיל שום כלי. הפעל כלי רק כאשר המשתמש ביקש במפורש פעולה בטלפון או פעולה מעשית שהכלי מתאים לה. אם הבקשה היא גם שאלה וגם פעולה, ענה בקצרה ובצע את הפעולה. הפעולות מועברות ישירות לאפליקציית Android ללא מסך אישור נוסף באתר. אל תבקש API key ואל תחשוף סודות. אל תטען שביצעת פעולה אם רק שלחת הוראה לביצוע.";
const TOOLS=[{"type":"function","function":{"name":"open_url","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"open_app","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"dial","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"call","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"sms","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"email","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"maps","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"camera","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"settings","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"back","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"home","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"recents","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"notifications","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"quick_settings","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"click_text","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"type_text","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"scroll","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"copy","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}}];
function safeArgs(s){try{return JSON.parse(s||"{}")}catch{return {}}}
function callsToActions(calls){return (calls||[]).map(c=>({type:c.function.name,...safeArgs(c.function.arguments)}))}

app.get("/api/health",(req,res)=>res.json({ok:true,groqConfigured:!!process.env.GROQ_API_KEY,service:"phone-agent"}));

app.post("/api/transcribe",upload.single("file"),async(req,res)=>{
 try{
  const key=process.env.GROQ_API_KEY;
  if(!key)return res.status(503).json({error:"Render פעיל, אבל GROQ_API_KEY לא מוגדר בשרת"});
  if(!req.file||!req.file.buffer?.length)return res.status(400).json({error:"לא התקבל קובץ קול"});
  const form=new FormData();
  form.append("file",new Blob([req.file.buffer],{type:req.file.mimetype||"audio/wav"}),req.file.originalname||"speech.wav");
  form.append("model","whisper-large-v3-turbo");
  form.append("language","he");
  form.append("response_format","json");
  form.append("temperature","0");
  const response=await fetch("https://api.groq.com/openai/v1/audio/transcriptions",{
   method:"POST",
   headers:{"Authorization":`Bearer ${key}`},
   body:form
  });
  const data=await response.json();
  if(!response.ok)return res.status(response.status).json({error:"Groq Whisper: "+(data?.error?.message||"שגיאה בתמלול הקול")});
  res.json({text:(data?.text||"").trim()});
 }catch(e){console.error("TRANSCRIBE_ERROR",e);res.status(500).json({error:"שגיאת תמלול בשרת: "+(e?.message||String(e))})}
});

app.post("/api/chat",async(req,res)=>{
 try{
  const key=process.env.GROQ_API_KEY;
  if(!key)return res.status(503).json({error:"השרת עדיין לא מחובר ל-GROQ_API_KEY"});
  const messages=Array.isArray(req.body?.messages)?req.body.messages.slice(-20):[];
  const response=await fetch("https://api.groq.com/openai/v1/chat/completions",{
   method:"POST",
   headers:{"Authorization":`Bearer ${key}`,"Content-Type":"application/json"},
   body:JSON.stringify({model:"openai/gpt-oss-20b",temperature:0.2,messages:[{role:"system",content:SYSTEM},...messages],tools:TOOLS,tool_choice:"auto",parallel_tool_calls:false})
  });
  const data=await response.json();
  if(!response.ok)return res.status(response.status).json({error:data?.error?.message||"שגיאת Groq"});
  const msg=data?.choices?.[0]?.message||{};
  const actions=callsToActions(msg.tool_calls);
  if(actions.length)return res.json({reply:"מבצע עכשיו.",actions});
  res.json({reply:msg.content||"לא התקבלה תשובה",actions:[]});
 }catch(e){res.status(500).json({error:"שגיאת שרת"})}
});
app.get("/{*splat}",(req,res)=>res.sendFile(path.join(__dirname,"public","index.html")));
app.listen(PORT,()=>console.log(`Phone Agent listening on ${PORT}`));