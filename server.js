}));
function normalizeHeCommand(input){
 const original=String(input||"").trim();
 const s=original.toLowerCase().replace(/[!?.,؛،]/g," ").replace(/\\s+/g," ").trim();
 const actions=[];
 const push=(type,obj={})=>actions.push({type,...obj});
 if(/(פתח|תפתח|תעבור|עבור).*הודעות/.test(s)&&/אינסט/.test(s)){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"messages"});return actions;}
 if(/(עבור|תעבור|פתח|תפתח).*רילס/.test(s)&&/אינסט/.test(s)){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"reels"});return actions;}
 if(/(עשה|תעשה|תעשי|שים|תשים).*לייק/.test(s)&&/אינסט/.test(s)){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"like"});return actions;}
 if(/(שמור|תשמור|לשמור).*פוסט/.test(s)&&/אינסט/.test(s)){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"save"});return actions;}
 const searchMatch=s.match(/(?:חפש|תחפש|לחפש|חיפוש)\s+(?:את\s+)?(.+?)(?:\s+באינסטגרם|\s+באינסטה)$/);
 if(searchMatch){const q=searchMatch[1].trim();if(q){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"search"});push("instagram_action",{action:"wait",value:"650"});push("instagram_action",{action:"type_text",value:q});push("instagram_action",{action:"wait",value:"300"});push("instagram_action",{action:"submit_search",value:q});return actions;}}
 // Combined voice command: Whisper may omit the first letter: "תח את אינסטגרם וחפש יוסי".
 const combinedSearch=s.match(/(?:פתח|תפתח|תח|פת)\s+(?:את\s+)?אינסט(?:גרם|ה)\s+(?:ו?חפש|ו?תחפש|ו?לחפש|ו?חיפוש)\s+(?:את\s+)?(.+)$/);
 if(combinedSearch){const q=combinedSearch[1].trim();if(q){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"search"});push("instagram_action",{action:"wait",value:"650"});push("instagram_action",{action:"type_text",value:q});push("instagram_action",{action:"wait",value:"300"});push("instagram_action",{action:"submit_search",value:q});return actions;}}
 // Common navigation commands in the current Instagram context.\n if(/^(תעבור|תעביר|תלך|עבור|לך)\s+(אל\s+)?(מסך\s+)?ההודעות$/.test(s)||s.includes("תעבור למסך ההודעות")){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"messages"});return actions;}\n if(/^(תעבור|תעביר|תלך|עבור|לך)\s+(אל\s+)?(מסך\s+)?הבית$/.test(s)||s.includes("תעבור למסך הבית")||s.includes("לעבור למסך הבית")){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"home"});return actions;}\n if(/^(תיגלול|תגלול|גלול|תגלול)\s+(קצת\s+)?(למעלה|למעלה\s+קצת)$/.test(s)||/תגלול.*למעלה/.test(s)){push("instagram_action",{action:"scroll",value:"up"});return actions;}\n if(/^(תיגלול|תגלול|גלול|תגלול)\s+(קצת\s+)?(למטה|למטה\s+קצת)$/.test(s)||/תגלול.*למטה/.test(s)){push("instagram_action",{action:"scroll",value:"down"});return actions;}\n // Profile and Reels navigation. Support common Hebrew pronunciation/transcription variants.\n if(/^(תעבור|תעביר|תלך|עבור|לך) (אל )?(מסך )?(הפרופיל|פרופיל)$/.test(s)||s.includes("תעבור למסך הפרופיל")||s.includes("עבור למסך הפרופיל")){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"profile"});return actions;}\n if(/^(תעבור|תעביר|תלך|עבור|לך) (אל )?(מסך )?(הרליס|רליס|הרילס|רילס)$/.test(s)||/תעבור למסך.*(רליס|רילס|רליס)/.test(s)){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"reels"});return actions;}\n // Deterministic navigation commands must never depend on an LLM choosing the wrong tool.
 if(/^(תעבור|תעביר|תלך|עבור|לך) (אל )?(מסך )?הבית$/.test(s)||s.includes("תעבור למסך הבית")||s.includes("לעבור למסך הבית")){
  push("home"); return actions;
 }
 if(/^(פתח|תפתח|תפתחתה|תפתחה) את? ?אינסטגרם$/.test(s)||s==="instagram"||s==="אינסטגרם"){
  push("open_app",{package:"com.instagram.android"}); return actions;
 }
 if(/^(סגור|תסגור|תסגור את|לסגור) (את )?אינסטגרם$/.test(s)||s.includes("תסגור את אינסטגרם")){
  push("back"); return actions;
 }
 if(/^(פתח|תפתח|תפתחתה|תפתחה) את? ?האפליקציה$/.test(s)||s.includes("תפתח את האפליקציה")){
  push("open_app",{package:"com.arilifelong.agent"}); return actions;
 }
 if(/^(סגור|תסגור|לסגור) (את )?האפליקציה$/.test(s)||s.includes("תשגור את האפליקציה")||s.includes("תסגור את האפליקציה")){
  push("close_current_app"); return actions;
 }
 if(/^(חזור|תחזור|אחורה|חזרה)$/.test(s)){push("back");return actions;}
 if(/^(פתח|תפתח) הגדרות$/.test(s)||s.includes("פתח את ההגדרות")){push("settings");return actions;}
 return null;
}
const INSTAGRAM_ACTION_TYPES=new Set(["open_app","instagram_action","click_text","click_content_description","click_role","type_text","send_text","long_click_text","tap","long_click","swipe","swipe_direction","scroll","scroll_repeat","scroll_until_text","click_repeat","screen_info","screenshot","back","like","follow","approve","open_chat_menu","pin","press_send"]);
function repairActions(actions,userText){
 const direct=normalizeHeCommand(userText);
 const candidate=direct || (Array.isArray(actions)?actions:[]);
 return candidate.filter(a=>{
  if(!a || !INSTAGRAM_ACTION_TYPES.has(String(a.type||"")))return false;
  if(a.type==="open_app" && String(a.package||"")!=="com.instagram.android")return false;
  return true;
 });
}
function selectModelTools(mode){
 const sets={
  instagram:["open_app","instagram_action","click_text","click_content_description","click_role","type_text","send_text","long_click_text","tap","long_click","swipe","swipe_direction","scroll","scroll_repeat","scroll_until_text","click_repeat","screen_info","screenshot","back","like","follow","approve","open_chat_menu","pin","press_send"],
  settings:["settings_action","system_action","click_text","tap","swipe","scroll","screen_info","back","home","volume","brightness","notifications"],
  overlay:["move_overlay","move_overlay_xy","resize_overlay","hide_overlay","show_overlay","screen_info","tap","click_text","back","home"],
  all:MODEL_TOOLS.map(t=>t.function.name)
 };
 const names=sets[mode]||sets.all;
 return MODEL_TOOLS.filter(t=>names.includes(t.function.name));
}
function safeArgs(s){try{return JSON.parse(s||"{}")}catch{return {}}}