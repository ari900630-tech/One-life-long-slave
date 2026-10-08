import express from "express";
import path from "path";
import { fileURLToPath } from "url";
import multer from "multer";
import crypto from "crypto";

const app=express();
const __dirname=path.dirname(fileURLToPath(import.meta.url));
const PORT=process.env.PORT||3000;
app.use(express.json({limit:"1mb"}));
app.use(express.static(path.join(__dirname,"public")));
const upload=multer({storage:multer.memoryStorage(),limits:{fileSize:25*1024*1024}});

const SYSTEM=`אתה עוזר אישי אמיתי שמדבר בעברית טבעית, פשוטה וזורמת כמו בן אדם. אל תדבר בשפה רובוטית, אל תשתמש בניסוחים מיותרים ואל תחזור על עצמך. בשיחה רגילה ענה כמו אדם: קצר כשאפשר, אבל עם תשובה מלאה כשצריך. אם המשתמש אומר תודה, שלום, כן, לא או מנהל שיחה — ענה באופן טבעי ואל תנסה לבצע פעולה. כשאתה לא מבין בקשה, אמור בפשטות "לא הבנתי, מה תרצה שאעשה?" ואל תנחש. לעולם אל תבצע פעולה בצד השרת. נתח את הבקשה והחזר actions שמתארים את הפעולה המוצעת. האפליקציה תציג הצעות והמשתמש יבחר מה לבצע. כשיש חוסר בהירות, הסבר בעברית בדיוק מה לא הבנת ומה חסר כדי לבצע. כשאפשר להבין חלק מהבקשה, ציין מה כן הבנת ומה לא הבנת, והצע רק את הפעולות שאפשר לבצע בבטחה. אחרי פעולה מוצלחת אל תגיד "בוצע" או "בוצע בהצלחה"; חזור להאזנה. אם פעולה נכשלה, אל תקריא שגיאת מערכת או לוג; אמור משפט אנושי קצר. אם המשתמש מתקן אותך, השתמש בתיקון והמשך. קיימים מצבי סוכן: all, instagram, settings, chats. השתמש במסך הנוכחי כקונטקסט. נסה fallback עד שלוש פעמים. אל תבקש API key מהמשתמש.`;
const TOOLS=[{"type":"function","function":{"name":"open_url","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"open_app","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"dial","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"call","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"sms","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"email","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"maps","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"camera","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"settings","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"back","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"home","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"recents","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"notifications","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"quick_settings","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"click_text","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"type_text","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"scroll","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"copy","description":"בצע את הפעולה הזו בטלפון Android.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"app_settings","description":"פתח את מסך ההגדרות של אפליקציה מסוימת במכשיר.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"},"position":{"type":"string"},"x":{"type":"number"},"y":{"type":"number"},"x1":{"type":"number"},"y1":{"type":"number"},"x2":{"type":"number"},"y2":{"type":"number"},"duration":{"type":"number"},"count":{"type":"integer"},"delay":{"type":"integer"}},"additionalProperties":true}}},{"type":"function","function":{"name":"play_store_search","description":"חפש אפליקציה או משחק בחנות Google Play.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"},"position":{"type":"string"},"x":{"type":"number"},"y":{"type":"number"},"x1":{"type":"number"},"y1":{"type":"number"},"x2":{"type":"number"},"y2":{"type":"number"},"duration":{"type":"number"},"count":{"type":"integer"},"delay":{"type":"integer"}},"additionalProperties":true}}},{"type":"function","function":{"name":"move_overlay","description":"הזז את החלונית הצפה למעלה או למטה.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"},"position":{"type":"string"},"x":{"type":"number"},"y":{"type":"number"},"x1":{"type":"number"},"y1":{"type":"number"},"x2":{"type":"number"},"y2":{"type":"number"},"duration":{"type":"number"},"count":{"type":"integer"},"delay":{"type":"integer"}},"additionalProperties":true}}},{"type":"function","function":{"name":"tap","description":"בצע נגיעה במקום מסוים במסך.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"},"position":{"type":"string"},"x":{"type":"number"},"y":{"type":"number"},"x1":{"type":"number"},"y1":{"type":"number"},"x2":{"type":"number"},"y2":{"type":"number"},"duration":{"type":"number"},"count":{"type":"integer"},"delay":{"type":"integer"}},"additionalProperties":true}}},{"type":"function","function":{"name":"long_click","description":"בצע לחיצה ארוכה במקום מסוים במסך.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"},"position":{"type":"string"},"x":{"type":"number"},"y":{"type":"number"},"x1":{"type":"number"},"y1":{"type":"number"},"x2":{"type":"number"},"y2":{"type":"number"},"duration":{"type":"number"},"count":{"type":"integer"},"delay":{"type":"integer"}},"additionalProperties":true}}},{"type":"function","function":{"name":"swipe","description":"בצע החלקה בין שתי נקודות במסך.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"},"position":{"type":"string"},"x":{"type":"number"},"y":{"type":"number"},"x1":{"type":"number"},"y1":{"type":"number"},"x2":{"type":"number"},"y2":{"type":"number"},"duration":{"type":"number"},"count":{"type":"integer"},"delay":{"type":"integer"}},"additionalProperties":true}}},{"type":"function","function":{"name":"scroll_repeat","description":"גלול שוב ושוב בתוך האפליקציה, למשל עד סוף הרשימה או מספר פעמים.","parameters":{"type":"object","properties":{"url":{"type":"string"},"package":{"type":"string"},"number":{"type":"string"},"text":{"type":"string"},"address":{"type":"string"},"subject":{"type":"string"},"body":{"type":"string"},"query":{"type":"string"},"direction":{"type":"string"},"position":{"type":"string"},"x":{"type":"number"},"y":{"type":"number"},"x1":{"type":"number"},"y1":{"type":"number"},"x2":{"type":"number"},"y2":{"type":"number"},"duration":{"type":"number"},"count":{"type":"integer"},"delay":{"type":"integer"}},"additionalProperties":true}}},{"type":"function","function":{"name":"screen_info","description":"קרא את הטקסט והכפתורים הנראים במסך.","parameters":{"type":"object","properties":{"text":{"type":"string"},"count":{"type":"integer"},"delay":{"type":"integer"},"direction":{"type":"string"},"max":{"type":"integer"},"stream":{"type":"string"},"value":{"type":"integer"},"action":{"type":"string"},"name":{"type":"string"},"routine_json":{"type":"string"},"width":{"type":"integer"},"x":{"type":"number"},"y":{"type":"number"}},"additionalProperties":true}}},{"type":"function","function":{"name":"click_repeat","description":"לחץ מספר פעמים על טקסט.","parameters":{"type":"object","properties":{"text":{"type":"string"},"count":{"type":"integer"},"delay":{"type":"integer"},"direction":{"type":"string"},"max":{"type":"integer"},"stream":{"type":"string"},"value":{"type":"integer"},"action":{"type":"string"},"name":{"type":"string"},"routine_json":{"type":"string"},"width":{"type":"integer"},"x":{"type":"number"},"y":{"type":"number"}},"additionalProperties":true}}},{"type":"function","function":{"name":"scroll_until_text","description":"גלול עד שטקסט מסוים נמצא.","parameters":{"type":"object","properties":{"text":{"type":"string"},"count":{"type":"integer"},"delay":{"type":"integer"},"direction":{"type":"string"},"max":{"type":"integer"},"stream":{"type":"string"},"value":{"type":"integer"},"action":{"type":"string"},"name":{"type":"string"},"routine_json":{"type":"string"},"width":{"type":"integer"},"x":{"type":"number"},"y":{"type":"number"}},"additionalProperties":true}}},{"type":"function","function":{"name":"screenshot","description":"צלם את המסך.","parameters":{"type":"object","properties":{"text":{"type":"string"},"count":{"type":"integer"},"delay":{"type":"integer"},"direction":{"type":"string"},"max":{"type":"integer"},"stream":{"type":"string"},"value":{"type":"integer"},"action":{"type":"string"},"name":{"type":"string"},"routine_json":{"type":"string"},"width":{"type":"integer"},"x":{"type":"number"},"y":{"type":"number"}},"additionalProperties":true}}},{"type":"function","function":{"name":"volume","description":"שנה עוצמת קול.","parameters":{"type":"object","properties":{"text":{"type":"string"},"count":{"type":"integer"},"delay":{"type":"integer"},"direction":{"type":"string"},"max":{"type":"integer"},"stream":{"type":"string"},"value":{"type":"integer"},"action":{"type":"string"},"name":{"type":"string"},"routine_json":{"type":"string"},"width":{"type":"integer"},"x":{"type":"number"},"y":{"type":"number"}},"additionalProperties":true}}},{"type":"function","function":{"name":"brightness","description":"שנה בהירות.","parameters":{"type":"object","properties":{"text":{"type":"string"},"count":{"type":"integer"},"delay":{"type":"integer"},"direction":{"type":"string"},"max":{"type":"integer"},"stream":{"type":"string"},"value":{"type":"integer"},"action":{"type":"string"},"name":{"type":"string"},"routine_json":{"type":"string"},"width":{"type":"integer"},"x":{"type":"number"},"y":{"type":"number"}},"additionalProperties":true}}},{"type":"function","function":{"name":"system_action","description":"פתח הגדרת מערכת מתאימה.","parameters":{"type":"object","properties":{"text":{"type":"string"},"count":{"type":"integer"},"delay":{"type":"integer"},"direction":{"type":"string"},"max":{"type":"integer"},"stream":{"type":"string"},"value":{"type":"integer"},"action":{"type":"string"},"name":{"type":"string"},"routine_json":{"type":"string"},"width":{"type":"integer"},"x":{"type":"number"},"y":{"type":"number"}},"additionalProperties":true}}},{"type":"function","function":{"name":"hide_overlay","description":"הסתר את החלונית.","parameters":{"type":"object","properties":{"text":{"type":"string"},"count":{"type":"integer"},"delay":{"type":"integer"},"direction":{"type":"string"},"max":{"type":"integer"},"stream":{"type":"string"},"value":{"type":"integer"},"action":{"type":"string"},"name":{"type":"string"},"routine_json":{"type":"string"},"width":{"type":"integer"},"x":{"type":"number"},"y":{"type":"number"}},"additionalProperties":true}}},{"type":"function","function":{"name":"show_overlay","description":"הצג את החלונית.","parameters":{"type":"object","properties":{"text":{"type":"string"},"count":{"type":"integer"},"delay":{"type":"integer"},"direction":{"type":"string"},"max":{"type":"integer"},"stream":{"type":"string"},"value":{"type":"integer"},"action":{"type":"string"},"name":{"type":"string"},"routine_json":{"type":"string"},"width":{"type":"integer"},"x":{"type":"number"},"y":{"type":"number"}},"additionalProperties":true}}},{"type":"function","function":{"name":"resize_overlay","description":"שנה את רוחב החלונית.","parameters":{"type":"object","properties":{"text":{"type":"string"},"count":{"type":"integer"},"delay":{"type":"integer"},"direction":{"type":"string"},"max":{"type":"integer"},"stream":{"type":"string"},"value":{"type":"integer"},"action":{"type":"string"},"name":{"type":"string"},"routine_json":{"type":"string"},"width":{"type":"integer"},"x":{"type":"number"},"y":{"type":"number"}},"additionalProperties":true}}},{"type":"function","function":{"name":"move_overlay_xy","description":"הזז את החלונית למיקום x,y.","parameters":{"type":"object","properties":{"text":{"type":"string"},"count":{"type":"integer"},"delay":{"type":"integer"},"direction":{"type":"string"},"max":{"type":"integer"},"stream":{"type":"string"},"value":{"type":"integer"},"action":{"type":"string"},"name":{"type":"string"},"routine_json":{"type":"string"},"width":{"type":"integer"},"x":{"type":"number"},"y":{"type":"number"}},"additionalProperties":true}}},{"type":"function","function":{"name":"save_routine","description":"שמור שגרה בשם; routine_json הוא מערך actions.","parameters":{"type":"object","properties":{"text":{"type":"string"},"count":{"type":"integer"},"delay":{"type":"integer"},"direction":{"type":"string"},"max":{"type":"integer"},"stream":{"type":"string"},"value":{"type":"integer"},"action":{"type":"string"},"name":{"type":"string"},"routine_json":{"type":"string"},"width":{"type":"integer"},"x":{"type":"number"},"y":{"type":"number"}},"additionalProperties":true}}},{"type":"function","function":{"name":"run_routine","description":"הפעל שגרה שמורה בשם.","parameters":{"type":"object","properties":{"text":{"type":"string"},"count":{"type":"integer"},"delay":{"type":"integer"},"direction":{"type":"string"},"max":{"type":"integer"},"stream":{"type":"string"},"value":{"type":"integer"},"action":{"type":"string"},"name":{"type":"string"},"routine_json":{"type":"string"},"width":{"type":"integer"},"x":{"type":"number"},"y":{"type":"number"}},"additionalProperties":true}}},{"type":"function","function":{"name":"send_text","description":"הקלד טקסט בשדה הפעיל ושלח.","parameters":{"type":"object","properties":{"text":{"type":"string"}},"additionalProperties":true}}},
{"type":"function","function":{"name":"long_click_text","description":"לחיצה ארוכה על טקסט או תיאור נגיש.","parameters":{"type":"object","properties":{"text":{"type":"string"},"target":{"type":"string"}},"additionalProperties":true}}},
{"type":"function","function":{"name":"click_content_description","description":"לחיצה לפי content description.","parameters":{"type":"object","properties":{"text":{"type":"string"},"target":{"type":"string"}},"additionalProperties":true}}},
{"type":"function","function":{"name":"click_role","description":"לחיצה לפי תפקיד נגיש.","parameters":{"type":"object","properties":{"text":{"type":"string"},"role":{"type":"string"}},"additionalProperties":true}}},
{"type":"function","function":{"name":"swipe_direction","description":"החלקה לפי כיוון.","parameters":{"type":"object","properties":{"direction":{"type":"string"}},"additionalProperties":true}}},
{"type":"function","function":{"name":"like","description":"עשה לייק לפריט הנראה כרגע.","parameters":{"type":"object","properties":{},"additionalProperties":true}}},
{"type":"function","function":{"name":"follow","description":"עקוב אחרי הפריט/חשבון הנראה כרגע.","parameters":{"type":"object","properties":{},"additionalProperties":true}}},
{"type":"function","function":{"name":"approve","description":"לחץ על אישור/אפשר/Confirm הנראה כרגע.","parameters":{"type":"object","properties":{},"additionalProperties":true}}},
{"type":"function","function":{"name":"open_notifications_and_click","description":"פתח התראות ולחץ על התראה לפי טקסט.","parameters":{"type":"object","properties":{"text":{"type":"string"},"target":{"type":"string"}},"additionalProperties":true}}},
{"type":"function","function":{"name":"long_click_notification","description":"פתח התראות ולחיצה ארוכה על התראה.","parameters":{"type":"object","properties":{"text":{"type":"string"},"target":{"type":"string"}},"additionalProperties":true}}},
{"type":"function","function":{"name":"click_quick_setting","description":"פתח הגדרות מהירות ולחץ על אריח.","parameters":{"type":"object","properties":{"text":{"type":"string"},"target":{"type":"string"}},"additionalProperties":true}}},
{"type":"function","function":{"name":"long_click_quick_setting","description":"פתח הגדרות מהירות ולחיצה ארוכה על אריח.","parameters":{"type":"object","properties":{"text":{"type":"string"},"target":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"open_chat_menu","description":"פתח את תפריט שלוש הנקודות/אפשרויות נוספות בצ׳אט.","parameters":{"type":"object","properties":{},"additionalProperties":true}}},{"type":"function","function":{"name":"pin","description":"הצמד/נעץ את הפריט הנראה כרגע.","parameters":{"type":"object","properties":{},"additionalProperties":true}}},{"type":"function","function":{"name":"press_send","description":"לחץ על כפתור השליחה הנראה כרגע.","parameters":{"type":"object","properties":{},"additionalProperties":true}}},{"type":"function","function":{"name":"open_notifications","description":"פתח את חלונית ההתראות.","parameters":{"type":"object","properties":{},"additionalProperties":true}}},{"type":"function","function":{"name":"uninstall_app","description":"הסר אפליקציה דרך ממשק Android. package הוא package name ו-app הוא השם המוצג.","parameters":{"type":"object","properties":{"package":{"type":"string"},"app":{"type":"string"}},"additionalProperties":true}}},{"type":"function","function":{"name":"chrome_new_tab","description":"פתח כרטיסייה חדשה ב-Chrome.","parameters":{"type":"object","properties":{},"additionalProperties":true}}},
{"type":"function","function":{"name":"chrome_close_tab","description":"סגור את הכרטיסייה הנוכחית ב-Chrome.","parameters":{"type":"object","properties":{},"additionalProperties":true}}},
{"type":"function","function":{"name":"chrome_next_tab","description":"עבור לכרטיסייה הבאה ב-Chrome.","parameters":{"type":"object","properties":{},"additionalProperties":true}}},
{"type":"function","function":{"name":"chrome_previous_tab","description":"עבור לכרטיסייה הקודמת ב-Chrome.","parameters":{"type":"object","properties":{},"additionalProperties":true}}},
{"type":"function","function":{"name":"chrome_clear_search","description":"מחק את הטקסט הקיים בשדה החיפוש או בשורת הכתובת של Chrome.","parameters":{"type":"object","properties":{},"additionalProperties":true}}},
{"type":"function","function":{"name":"settings_action","description":"בצע פעולה בהגדרות Android לפי המסך הנוכחי. פעולות: open, wifi, bluetooth, sound, volume, display, brightness, battery, apps, notifications, privacy, security, storage, language, date_time, accessibility, permissions, accounts, location, screen_lock, search, click, scroll, back.","parameters":{"type":"object","properties":{"action":{"type":"string"},"value":{"type":"string"}},"required":["action"],"additionalProperties":true}}},{"type":"function","function":{"name":"instagram_action","description":"בצע פעולה באינסטגרם לפי מה שרואים כרגע. פעולות: like, save, share, comment, follow, unfollow, search, profile, home, reels, stories, messages, new_post, next, previous, back, type_comment, send, open_result.","parameters":{"type":"object","properties":{"action":{"type":"string"},"value":{"type":"string"}},"required":["action"],"additionalProperties":true}}},{"type":"function","function":{"name":"uninstall_current_app","description":"הסר את האפליקציה הפתוחה כרגע דרך מסך הבית.","parameters":{"type":"object","properties":{},"additionalProperties":true}}}];
const MODEL_TOOLS=TOOLS.map(t=>({type:"function",function:{name:t.function.name,description:String(t.function.description||"").slice(0,120),parameters:{type:"object",additionalProperties:true}}}));
function selectModelTools(mode){
 const sets={
  instagram:["instagram_action","click_text","click_content_description","type_text","send_text","tap","swipe","scroll","screen_info","screenshot","back","home"],
  settings:["settings_action","system_action","click_text","tap","swipe","scroll","screen_info","back","home","volume","brightness","notifications"],
  overlay:["move_overlay","move_overlay_xy","resize_overlay","hide_overlay","show_overlay","screen_info","tap","click_text","back","home"],
  all:["open_app","open_url","dial","call","sms","maps","click_text","type_text","tap","swipe","scroll","screen_info","back","home","settings_action"]
 };
 const names=sets[mode]||sets.all;
 return MODEL_TOOLS.filter(t=>names.includes(t.function.name));
}
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
  const rawMessages=Array.isArray(req.body?.messages)?req.body.messages:[];
  const lastUser=rawMessages.filter(m=>m?.role==="user").at(-1)?.content||"";
  const conversational=/^(תודה|תודה רבה|שלום|היי|הי|אהלן|אוקיי|בסדר|מעולה|כן|לא|ביי|להתראות|לילה טוב|בוקר טוב|ערב טוב)[!. ,]*$/iu.test(String(lastUser).trim());
  const messages=conversational?[]:rawMessages.slice(-5).map(m=>({...m,content:typeof m.content==="string"?m.content.slice(-1000):m.content}));
  // אם למודל אחד נגמרת מכסת הטוקנים/Rate Limit, עוברים אוטומטית למודל אחר שעדיין זמין.
  const models=["openai/gpt-oss-20b","openai/gpt-oss-120b","qwen/qwen3.8-27b"];
  let response=null;
  let data=null;
  let lastError="";
  for(const model of models){
   try{
    response=await fetch("https://api.groq.com/openai/v1/chat/completions",{
     method:"POST",
     headers:{"Authorization":"Bearer "+key,"Content-Type":"application/json"},
     body:JSON.stringify(conversational?{model,temperature:0.35,messages:[{role:"system",content:SYSTEM},{role:"user",content:String(lastUser).trim()}],tool_choice:"none"}:{model,temperature:0.2,messages:[{role:"system",content:SYSTEM},...messages],tools:selectModelTools(String(req.body?.mode||"all").toLowerCase()),tool_choice:"auto",parallel_tool_calls:false})
    });
    data=await response.json();
    if(response.ok)break;
    lastError=data?.error?.message||("HTTP "+response.status);
    const retryable=response.status===413 || response.status===429 || response.status===408 || response.status===503;
    if(!retryable)break;
   }catch(e){
    lastError=e?.message||String(e);
    response=null;
   }
  }
  if(!response || !response.ok)return res.status(response?.status||503).json({error:lastError||"כל מודלי ה-AI אינם זמינים כרגע"});
  const msg=data?.choices?.[0]?.message||{};
  const actions=callsToActions(msg.tool_calls);
  const suggestions=(String(req.body?.mode||"all").toLowerCase()==="instagram")?["עשה לייק","פתח הודעות","עבור לרילס","חפש באינסטגרם","עבור לפוסט הבא","שמור את הפוסט"]:["פתח הגדרות","פתח Chrome","חזור אחורה","עבור למסך הבית","פתח התראות","העלה עוצמת קול"];
  if(actions.length){ const names=actions.map(a=>a.type).filter(Boolean); return res.json({reply:msg.content||"",actions,actionSummary:names,unclear:msg.content||""}); }
  res.json({reply:msg.content||"לא התקבלה תשובה",actions:[]});
 }catch(e){res.status(500).json({error:"שגיאת שרת"})}
});
const remoteDevices=new Map();
const remoteQueue=new Map();
function remoteToken(){return "ra_"+crypto.randomUUID().replace(/-/g,"");}
app.post("/api/remote/register",(req,res)=>{
 const deviceId=String(req.body?.deviceId||"").trim();
 if(!deviceId)return res.status(400).json({error:"deviceId required"});
 const token=remoteDevices.get(deviceId)||remoteToken();
 remoteDevices.set(deviceId,token);
 if(!remoteQueue.has(token))remoteQueue.set(token,[]);
 res.json({ok:true,token});
});
app.get("/api/remote/poll",(req,res)=>{
 const token=String(req.query?.token||"").trim();
 if(!token||![...remoteDevices.values()].includes(token))return res.status(401).json({error:"unauthorized"});
 const q=remoteQueue.get(token)||[];
 const item=q.shift(); remoteQueue.set(token,q);
 res.json(item?{ok:true,command:item}:{ok:true,command:null});
});
app.get("/api/remote/send",(req,res)=>{
 const token=String(req.query?.token||"").trim();
 const text=String(req.query?.text||"").trim();
 if(!token||![...remoteDevices.values()].includes(token))return res.status(401).json({error:"unauthorized"});
 if(!text)return res.status(400).json({error:"text required"});
 const q=remoteQueue.get(token)||[]; const id=crypto.randomUUID();
 q.push({id,text,createdAt:Date.now()}); remoteQueue.set(token,q);
 res.json({ok:true,id,queued:true});
});
// ChatGPT/automation bridge: a separate, server-authenticated command channel.
// It never executes commands on Render; it only queues them for the registered Android agent.
function authorizeChatBridge(req){
 const expected=String(process.env.CHAT_AGENT_KEY||"").trim();
 const supplied=String(req.get("x-agent-key")||req.query?.key||"").trim();
 return !!expected && crypto.timingSafeEqual(Buffer.from(supplied),Buffer.from(expected));
}
app.post("/api/remote/command",(req,res)=>{
 if(!authorizeChatBridge(req))return res.status(401).json({error:"unauthorized"});
 const text=String(req.body?.text||"").trim();
 const deviceId=String(req.body?.deviceId||"").trim();
 if(!text)return res.status(400).json({error:"text required"});
 let targets=[];
 if(deviceId){
  const token=remoteDevices.get(deviceId);
  if(token)targets.push(token);
 }else{
  targets=[...remoteDevices.values()];
 }
 if(!targets.length)return res.status(409).json({error:"no registered Android device"});
 const id=crypto.randomUUID();
 for(const token of targets){
  const q=remoteQueue.get(token)||[];
  q.push({id,text,createdAt:Date.now(),source:"chat"});
  remoteQueue.set(token,q);
 }
 res.json({ok:true,id,queued:true,devices:targets.length});
});
app.get("/api/remote/bridge-status",(req,res)=>{
 if(!authorizeChatBridge(req))return res.status(401).json({error:"unauthorized"});
 const devices=[...remoteDevices.entries()].map(([deviceId,token])=>({deviceId,queued:(remoteQueue.get(token)||[]).length}));
 res.json({ok:true,bridge:true,devices});
});
app.get("/api/remote/status",(req,res)=>{
 const token=String(req.query?.token||"").trim();
 if(!token||![...remoteDevices.values()].includes(token))return res.status(401).json({error:"unauthorized"});
 res.json({ok:true,connected:true,queued:(remoteQueue.get(token)||[]).length});
});
app.get("/{*splat}",(req,res)=>res.sendFile(path.join(__dirname,"public","index.html")));
app.listen(PORT,()=>console.log(`Phone Agent listening on ${PORT}`));