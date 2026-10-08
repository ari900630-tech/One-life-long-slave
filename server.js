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
 if(/^(תעבור|תעביר|תלך|עבור|לך) (אל )?(מסך )?(ההודעות|הודעות)$/.test(s)||s.includes("תעבור למסך ההודעות")){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"messages"});return actions;}\n if(/^(תעבור|תעביר|תלך|עבור|לך) (אל )?(מסך )?(הפרופיל|פרופיל)$/.test(s)||s.includes("תעבור למסך הפרופיל")){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"profile"});return actions;}\n if(/^(תעבור|תעביר|תלך|עבור|לך) (אל )?(מסך )?(הרליס|רליס|הרילס|רילס)$/.test(s)||/תעבור למסך.*(רליס|רילס)/.test(s)){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"reels"});return actions;}\n if(/^(תיגלול|תגלול|גלול) (קצת )?(למעלה|למטה)$/.test(s)||/ת(?:י)?גלול.*למעלה/.test(s)){push("instagram_action",{action:"scroll",value:"up"});return actions;}\n if(/^(תיגלול|תגלול|גלול) (קצת )?למטה$/.test(s)||/ת(?:י)?גלול.*למטה/.test(s)){push("instagram_action",{action:"scroll",value:"down"});return actions;}\n if(/^(תעבור|תעביר|תלך|עבור|לך) (אל )?(מסך )?(ההתראות|התראות)$/.test(s)||s.includes("תעבור למסך ההתראות")){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"notifications"});return actions;}\n // Deterministic navigation commands must never depend on an LLM choosing the wrong tool.
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