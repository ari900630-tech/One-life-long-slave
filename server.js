import express from "express";
import path from "path";
import { fileURLToPath } from "url";
import multer from "multer";
import crypto from "crypto";
import { createMcpHandler, McpServer } from "@modelcontextprotocol/server";
import { toNodeHandler } from "@modelcontextprotocol/node";
import * as z from "zod/v4";

const app=express();
const __dirname=path.dirname(fileURLToPath(import.meta.url));
const PORT=process.env.PORT||3000;
app.use(express.json({limit:"1mb"}));
app.use(express.urlencoded({extended:false,limit:"1mb"}));
app.use(express.static(path.join(__dirname,"public")));
const upload=multer({storage:multer.memoryStorage(),limits:{fileSize:25*1024*1024}});

const SYSTEM=`מצב הסוכן נעול ל-Instagram בלבד. מותר לפתוח רק את com.instagram.android ולבצע פעולות בתוך Instagram: קריאת מסך/עץ נגישות, לחיצה לפי טקסט או content-description או role, הקלדה, שליחה, גלילה, החלקה, לחיצה ארוכה, חיפוש, פרופילים, פוסטים, רילס, סטוריז, הודעות, מסך הפעילות/הלב וההתראות של Instagram וניווט בתוך Instagram. מותר לפתוח את מסך הפעילות/הלב של Instagram, אבל אסור לפתוח את חלונית ההתראות של Android. אסור לחלוטין לפתוח או לשלוט ב-Chrome, ChatGPT, הגדרות Android, מסך הבית, הגדרות מהירות, SMS, שיחות, מפות, Play Store או כל אפליקציה אחרת. אין להחזיר open_url, home, recents, notifications, quick_settings, settings או פעולה מערכתית. אם צריך להגיע ל-Instagram, החזר open_app עם package=com.instagram.android ואז המשך. בצע רצפים של כמה פעולות Instagram לפי פקודה אחת. בצע לייק, מעקב, תגובה, שליחת הודעה, פרסום או מחיקה רק כשהמשתמש ביקש במפורש. אל תבצע ספאם או פעולות המוניות.\nאתה עוזר אישי אמיתי שמדבר בעברית טבעית, פשוטה וזורמת כמו בן אדם. לפני שאתה מפרש בקשה, תקן בראש שגיאות כתיב, שגיאות הקלדה, מילים חסרות, תמלול קולי משובש, ניסוח לא מדויק ושמות שנכתבו בצורה מעט שונה. נסה להבין את הכוונה לפי כל המשפט, ההקשר של ההודעות הקודמות, הפקודות שבוצעו והמסך הנוכחי. לדוגמה, אם המשתמש כתב מילה שגויה אבל ברור מה הייתה הכוונה, השתמש בצורה המתוקנת בלי לעצור אותו על הטעות. אם יש שתי פרשנויות סבירות, השתמש במסך הנוכחי ובהקשר כדי לבחור את הפרשנות הסבירה ביותר; התייחס גם למילים שנשמעו דומה או הוכתבו בצורה שגויה. אם משפט חדש מתייחס ל"זה", "שם", "אותו", "הבא", "האחרון", "מה שאמרתי קודם" וכדומה, חפש למה הוא מתייחס בהודעות הקודמות לפני שאתה מפרש אותו. אל תבקש אישור לפעולות רגילות. שמור על המשמעות המקורית ואל תמציא פרטים. בשיחה רגילה ענה כמו אדם: קצר כשאפשר, אבל עם תשובה מלאה כשצריך. אם המשתמש אומר תודה, שלום, כן, לא או מנהל שיחה — ענה באופן טבעי ואל תנסה לבצע פעולה. כשאתה לא מבין בקשה, אמור בפשטות "לא הבנתי, מה תרצה שאעשה?" ואל תנחש. לעולם אל תבצע פעולה בצד השרת. נתח את הבקשה והחזר actions שמתארים את הפעולה המוצעת. האפליקציה מבצעת את ה-actions ישירות. אל תייצר שלב אישור ואל תחכה לאישור. כשיש חוסר בהירות, הסבר בעברית בדיוק מה לא הבנת ומה חסר כדי לבצע. כשאפשר להבין חלק מהבקשה, ציין מה כן הבנת ומה לא הבנת, והצע רק את הפעולות שאפשר לבצע בבטחה. אחרי פעולה מוצלחת אל תגיד "בוצע" או "בוצע בהצלחה"; חזור להאזנה. אם פעולה נכשלה, אל תקריא שגיאת מערכת או לוג; אמור משפט אנושי קצר. אם המשתמש מתקן אותך, השתמש בתיקון והמשך. אל תתייחס לתמלול הקולי כטקסט מושלם: תקן שיבושי תמלול לפי ההקשר, כולל מילים עבריות דומות בצליל, אותיות חסרות, מילים מחוברות ושמות אפליקציות. אל תשנה כוונה רק בגלל שגיאת תמלול. קיימים מצבי סוכן: all, instagram, settings, chats. השתמש במסך הנוכחי כקונטקסט. נסה fallback עד שלוש פעמים. אל תבקש API key מהמשתמש.`;
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
{"type":"function","function":{"name":"settings_action","description":"בצע פעולה בהגדרות Android לפי המסך הנוכחי. פעולות: open, wifi, bluetooth, sound, volume, display, brightness, battery, apps, notifications, privacy, security, storage, language, date_time, accessibility, permissions, accounts, location, screen_lock, search, click, scroll, back.","parameters":{"type":"object","properties":{"action":{"type":"string"},"value":{"type":"string"}},"required":["action"],"additionalProperties":true}}},{"type":"function","function":{"name":"instagram_action","description":"בצע פעולה באינסטגרם לפי מה שרואים כרגע. פעולות: like, save, share, comment, follow, unfollow, search, profile, home, reels, stories, messages, new_post, next, previous, back, type_comment, send, open_result, submit_search, wait, click, click_text, click_content_description, long_click, type_text, scroll, swipe_direction. בפעולה submit_search העבר value עם טקסט החיפוש.","parameters":{"type":"object","properties":{"action":{"type":"string"},"value":{"type":"string"}},"required":["action"],"additionalProperties":true}}},{"type":"function","function":{"name":"uninstall_current_app","description":"הסר את האפליקציה הפתוחה כרגע דרך מסך הבית.","parameters":{"type":"object","properties":{},"additionalProperties":true}}}];
// Keep the real parameter schemas. The previous code stripped them, so the model could call
// open_app with an empty package (as seen in the Android log for "תעבור למסך הבית").
const MODEL_TOOLS=TOOLS.map(t=>({
 type:"function",
 function:{
  name:t.function.name,
  description:String(t.function.description||"").slice(0,220),
  parameters:t.function.parameters||{type:"object",additionalProperties:true}
 }
}));
function normalizeHeCommand(input){
 const original=String(input||"").trim();
 const s=original.toLowerCase().replace(/[!?.,؛،]/g," ").replace(/\s+/g," ").trim();
 const actions=[];
 const push=(type,obj={})=>actions.push({type,...obj});
 // Commands that mean "close the current assistant and open Instagram" are a single
 // navigation intent. Never emit close_current_app because the Android runtime is
 // intentionally locked to Instagram actions.
 if(/(תסגור|סגור|לסגור).*?(האפליקציה|האפליקצייה|היישום).*?(ותפתח|ואז תפתח|ולפתוח|ואז לפתוח).*?אינסטגרם/.test(s) ||
    /(תסגור|סגור|לסגור).*?(האפליקציה|האפליקצייה|היישום).*?(ו|ואז).*?(פתח|תפתח|לפתוח).*?אינסטגרם/.test(s) ||
    /פתח.*?אינסטגרם.*?(אחרי|לאחר).*?(סגור|סגירת).*?(האפליקציה|האפליקצייה)/.test(s)){
   push("open_app",{package:"com.instagram.android"});
   return actions;
 }
 if(/(פתח|תפתח|תעבור|עבור).*הודעות/.test(s)&&/אינסט/.test(s)){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"messages"});return actions;}
 if(/(פתח|תפתח|תעבור|עבור|לך|תלך).*התראות/.test(s)&&(/אינסט/.test(s)||/מסך ההתראות|להתראות/.test(s))){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"notifications"});return actions;}
 if(/(תעבור|עבור|פתח|תפתח|לך|תלך).*חיפוש( באינסטגרם)?$/.test(s)||/מסך החיפוש/.test(s)){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"search"});return actions;}
 if(/((תן|תעשה|שים|תעשה לי|תן לי).*לייק.*(סרטון|סירטון|וידאו|רילס)|^(לייק|לייק לסרטון|תן לייק|תן לייק לסרטון)$)/.test(s)){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"like"});return actions;}
 if(/((שתף|תשתף|לשתף|שיתוף).*?(סרטון|סירטון|וידאו|רילס)|^(שתף את זה|שתף סרטון)$)/.test(s)){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"share"});return actions;}
 if(/((שמור|תשמור|לשמור).*?(סרטון|סירטון|וידאו|רילס)|^(שמור את זה|שמור סרטון)$)/.test(s)){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"save"});return actions;}
 if(/(עבור|תעבור|פתח|תפתח).*רילס/.test(s)&&/אינסט/.test(s)){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"reels"});return actions;}
 if(/(עשה|תעשה|תעשי|שים|תשים).*לייק/.test(s)&&/אינסט/.test(s)){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"like"});return actions;}
 if(/(שמור|תשמור|לשמור).*פוסט/.test(s)&&/אינסט/.test(s)){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"save"});return actions;}
 const searchMatch=s.match(/(?:חפש|תחפש|לחפש|חיפוש)\s+(?:את\s+)?(.+?)(?:\s+באינסטגרם|\s+באינסטה)$/);
 const combinedSearch=s.match(/(?:פתח|תפתח|תעבור|עבור|לך|תלך|תחפש|חפש|לחפש|חיפוש).*?(?:באינסטגרם|באינסטה)?\s*(?:וחפש|חפש)\s+(?:את\s+)?(.+?)(?:\s+באינסטגרם|\s+באינסטה)?$/);
 if(combinedSearch){
  const q=combinedSearch[1].trim();
  if(q){
   push("open_app",{package:"com.instagram.android"});
   push("instagram_action",{action:"search"});
   push("instagram_action",{action:"wait",value:"650"});
   push("instagram_action",{action:"type_text",value:q});
   push("instagram_action",{action:"wait",value:"300"});
   push("instagram_action",{action:"submit_search",value:q});
   return actions;
  }
 }
 if(searchMatch){const q=searchMatch[1].trim();if(q){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"search"});push("instagram_action",{action:"wait",value:"650"});push("instagram_action",{action:"type_text",value:q});push("instagram_action",{action:"wait",value:"300"});push("instagram_action",{action:"submit_search",value:q});return actions;}}
 // Deterministic navigation commands must never depend on an LLM choosing the wrong tool.
 if(/(תעבור|עבור|לך|תלך|פתח|תפתח).*?(כפתור החיפוש|מסך החיפוש|חיפוש באינסטגרם|לחיפוש)/.test(s)){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"search"});return actions;}
 if(/(תעבור|עבור|לך|תלך|פתח|תפתח|לחץ|תלחץ).*?(כפתור הלב|הלב|מסך ההתראות|התראות באינסטגרם|ההתראות באינסטגרם|פעילות באינסטגרם)/.test(s)){push("open_app",{package:"com.instagram.android"});push("instagram_action",{action:"notifications"});return actions;}
 if(/(?:^|\s)(?:גלול|תגלול|גלילה|לגלול|תרד|רד|תוריד|תעלה|עלה|תעבור|עבור)\s*(?:קצת\s*)?(?:למטה|למעלה)(?:\s|$)/.test(s)||/(?:למטה|למעלה).*?(?:גלול|תגלול|לגלול)/.test(s)){
  const up=/(למעלה|עלה|תעלה)/.test(s);
  push("open_app",{package:"com.instagram.android"});
  push("instagram_action",{action:"scroll",value:up?"up":"down"});
  return actions;
 }
 if(/^(תעבור|תעביר|תלך|עבור|לך) (אל )?(מסך )?הבית$/.test(s)||s.includes("תעבור למסך הבית")||s.includes("לעבור למסך הבית")){
  push("open_app",{package:"com.instagram.android"}); push("instagram_action",{action:"home"}); return actions;
 }
 if(/^(פתח|תפתח|תפתחתה|תפתחה|תח) את? ?אינסטגרם$/.test(s)||s==="instagram"||s==="אינסטגרם"){
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
function extractInstagramSearchQuery(input){
 const s=String(input||"").replace(/[!?.,;؛،]/g," ").replace(/\s+/g," ").trim();
 if(!s)return "";
 const patterns=[
  /(?:פתח|תפתח|כנס|תיכנס|להיכנס|היכנס|תעבור|עבור|לך|תלך).*?(?:לאינסטגרם|לנסטגרם|באינסטגרם|באינסטה|לינסטגרם|אינסטגרם|אינסטה).*?(?:וחפש|ותחפש|חפש|תחפש|לחפש|חיפוש)\s+(?:את\s+)?(.+?)(?:\s+באינסטגרם|\s+באינסטה)?$/iu,
  /(?:חפש|תחפש|לחפש|חיפוש)\s+(?:את\s+)?(.+?)\s+(?:באינסטגרם|באינסטה)$/iu,
  /(?:חפש|תחפש|לחפש|חיפוש)\s+(?:באינסטגרם|באינסטה)\s+(?:את\s+)?(.+)$/iu
 ];
 for(const re of patterns){const m=s.match(re);if(m&&m[1]){const q=m[1].trim();if(q&&!/^(באינסטגרם|באינסטה)$/iu.test(q))return q;}}
 return "";
}
function repairActions(actions,userText){
 const searchQuery=extractInstagramSearchQuery(userText);
 if(searchQuery)return [
  {type:"open_app",package:"com.instagram.android"},
  {type:"instagram_action",action:"search"},
  {type:"instagram_action",action:"wait",value:"650"},
  {type:"instagram_action",action:"type_text",value:searchQuery},
  {type:"instagram_action",action:"wait",value:"300"},
  {type:"instagram_action",action:"submit_search",value:searchQuery}
 ];
 const direct=normalizeHeCommand(userText);
 const candidate=direct || (Array.isArray(actions)?actions:[]);
 return candidate.filter(a=>{if(!a||!INSTAGRAM_ACTION_TYPES.has(String(a.type||"")))return false;if(a.type==="open_app"&&String(a.package||"")!=="com.instagram.android")return false;return true;});
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
function callsToActions(calls){
 if(!Array.isArray(calls))return [];
 return calls.map(c=>({type:c?.function?.name,...safeArgs(c?.function?.arguments)})).filter(x=>x.type);
}
function actionsFromModelContent(content){
 const raw=String(content||"").trim();
 if(!raw)return [];
 const candidates=[];
 candidates.push(raw);
 const fenced=raw.match(/\`\`\`(?:json)?\\s*([\\s\\S]*?)\`\`\`/i);
 if(fenced)candidates.push(fenced[1].trim());
 const start=raw.indexOf("[");
 const end=raw.lastIndexOf("]");
 if(start>=0&&end>start)candidates.push(raw.slice(start,end+1));
 for(const candidate of candidates){
  try{
   const parsed=JSON.parse(candidate);
   if(!Array.isArray(parsed))continue;
   const out=parsed.map(x=>{
    if(!x||typeof x!=="object")return null;
    if(x.type)return x;
    if(x.name)return {type:x.name,arguments:x.arguments||{}};
    return null;
   }).filter(Boolean).map(x=>{
    if(x.arguments&&typeof x.arguments==="object")return {type:x.type,...x.arguments};
    return x;
   });
   if(out.length)return out;
  }catch(_){}
 }
 return [];
}

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
  const normalizedLast=String(lastUser).replace(/\s+/g," ").trim();
  // If speech recognition cuts off before the search term, ask for the missing term.
  const incompleteSearch=/(?:פתח|תפתח|תעבור|עבור|לך|תלך|להיכנס|היכנס|כנס).*?(?:אינסטגרם|אינסטה).*?(?:וחפש|ולחפש|חפש|לחפש)\s*(?:את)?\s*$/iu.test(normalizedLast) ||
    /^(?:חפש|תחפש|לחפש|חיפוש)\s*(?:באינסטגרם|באינסטה)?\s*(?:את)?\s*$/iu.test(normalizedLast);
  if(incompleteSearch){
   return res.json({reply:"לא שמעתי מה לחפש באינסטגרם. אמור לי מה לחפש, למשל: חפש באינסטגרם יוסי.",actions:[]});
  }
  const conversational=/^(תודה|תודה רבה|שלום|היי|הי|אהלן|אוקיי|בסדר|מעולה|כן|לא|ביי|להתראות|לילה טוב|בוקר טוב|ערב טוב|מה קורה|מה קורא|מה קוראה)[!. ,?]*$/iu.test(normalizedLast);
  const messages=conversational?[]:rawMessages.slice(-10).map(m=>({...m,content:typeof m.content==="string"?m.content.slice(-1000):m.content}));
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
     body:JSON.stringify(conversational?{model,temperature:0.35,messages:[{role:"system",content:SYSTEM},{role:"user",content:String(lastUser).trim()}],tool_choice:"none"}:{model,temperature:0.2,messages:[{role:"system",content:SYSTEM},...messages],tools:selectModelTools("instagram"),tool_choice:"auto",parallel_tool_calls:false})
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
  const modelActions=callsToActions(msg.tool_calls);
  const contentActions=modelActions.length?[]:actionsFromModelContent(msg.content);
  const actions=repairActions(modelActions.length?modelActions:contentActions,lastUser);
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

/*
 * Claude / MCP remote-control bridge.
 * Claude connects to /mcp and can call tools that only enqueue commands
 * for the registered Android agent. Commands are never executed by Render.
 */

// Minimal OAuth 2.1 authorization server for Claude Custom Connectors.
// Access tokens are short-lived in-memory credentials scoped to this MCP server.
const oauthCodes=new Map();
const oauthTokens=new Map();
const oauthClients=new Map();
const OAUTH_ISSUER="https://one-life-long-slave.onrender.com";
const OAUTH_RESOURCE=OAUTH_ISSUER+"/mcp";

function oauthRedirectAllowed(uri){
 try{
  const u=new URL(uri);
  return u.protocol==="https:" && (u.hostname==="claude.ai" || u.hostname.endsWith(".claude.ai") || u.hostname==="anthropic.com" || u.hostname.endsWith(".anthropic.com"));
 }catch{return false}
}
function oauthMetadata(){
 return {
  issuer:OAUTH_ISSUER,
  authorization_endpoint:OAUTH_ISSUER+"/authorize",
  token_endpoint:OAUTH_ISSUER+"/token",
  registration_endpoint:OAUTH_ISSUER+"/register",
  response_types_supported:["code"],
  grant_types_supported:["authorization_code"],
  token_endpoint_auth_methods_supported:["none"],
  code_challenge_methods_supported:["S256"],
  scopes_supported:["mcp"],
  client_id_metadata_document_supported:true,
  authorization_response_iss_parameter_supported:true
 };
}
app.get("/.well-known/oauth-protected-resource",(req,res)=>res.json({
 resource:OAUTH_RESOURCE,
 authorization_servers:[OAUTH_ISSUER],
 scopes_supported:["mcp"]
}));
app.get("/.well-known/oauth-protected-resource/mcp",(req,res)=>res.json({
 resource:OAUTH_RESOURCE,
 authorization_servers:[OAUTH_ISSUER],
 scopes_supported:["mcp"]
}));
app.get("/.well-known/oauth-authorization-server",(req,res)=>res.json(oauthMetadata()));

app.post("/register",(req,res)=>{
 const body=req.body||{};
 const redirectUris=Array.isArray(body.redirect_uris)?body.redirect_uris.map(String):[];
 if(!redirectUris.length || redirectUris.some(u=>!oauthRedirectAllowed(u))){
  return res.status(400).json({error:"invalid_client_metadata"});
 }
 const clientId="client_"+crypto.randomUUID().replace(/-/g,"");
 oauthClients.set(clientId,{redirect_uris:redirectUris,client_name:String(body.client_name||"Claude")});
 res.status(201).json({
  client_id:clientId,
  client_name:String(body.client_name||"Claude"),
  redirect_uris:redirectUris,
  token_endpoint_auth_method:"none",
  grant_types:["authorization_code"],
  response_types:["code"]
 });
});

app.get("/authorize",(req,res)=>{
 const clientId=String(req.query.client_id||"");
 const redirectUri=String(req.query.redirect_uri||"");
 const responseType=String(req.query.response_type||"");
 const state=String(req.query.state||"");
 const challenge=String(req.query.code_challenge||"");
 const scope=String(req.query.scope||"mcp");
 if(responseType!=="code"||!clientId||!oauthRedirectAllowed(redirectUri)||!challenge){
  return res.status(400).send("Invalid OAuth authorization request.");
 }
 const registered=oauthClients.get(clientId);
 const clientLooksLikeCimd=/^https:\/\//.test(clientId);
 if(registered && !registered.redirect_uris.includes(redirectUri)){
  return res.status(400).send("Invalid redirect URI.");
 }
 if(!registered && !clientLooksLikeCimd){
  return res.status(400).send("Unknown OAuth client.");
 }
 const payload=Buffer.from(JSON.stringify({clientId,redirectUri,state,challenge,scope})).toString("base64url");
 res.type("html").send(`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>הסוכן שלי</title></head><body style="font-family:sans-serif;max-width:520px;margin:50px auto;padding:20px;text-align:center"><h2>חיבור Claude ל"הסוכן שלי"</h2><p>Claude מבקש הרשאה להשתמש בכלי השליטה של הסוכן בטלפון.</p><form method="POST" action="/authorize/approve"><input type="hidden" name="request" value="${payload}"><button style="font-size:18px;padding:12px 24px" type="submit">אישור חיבור</button></form></body></html>`);
});
app.post("/authorize/approve",(req,res)=>{
 try{
  const raw=Buffer.from(String(req.body?.request||""),"base64url").toString("utf8");
  const p=JSON.parse(raw);
  if(!oauthRedirectAllowed(p.redirectUri)||!p.challenge)throw new Error("invalid");
  const code=crypto.randomUUID().replace(/-/g,"");
  oauthCodes.set(code,{...p,expiresAt:Date.now()+5*60*1000});
  const u=new URL(p.redirectUri);
  u.searchParams.set("code",code);
  if(p.state)u.searchParams.set("state",p.state);
  u.searchParams.set("iss",OAUTH_ISSUER);
  res.redirect(u.toString());
 }catch{res.status(400).send("Invalid OAuth request.")}
});
app.post("/token",(req,res)=>{
 try{
  const grant=String(req.body?.grant_type||"");
  const code=String(req.body?.code||"");
  const redirectUri=String(req.body?.redirect_uri||"");
  const verifier=String(req.body?.code_verifier||"");
  if(grant!=="authorization_code"||!code||!verifier)return res.status(400).json({error:"invalid_request"});
  const item=oauthCodes.get(code);
  if(!item||item.expiresAt<Date.now()||item.redirectUri!==redirectUri)return res.status(400).json({error:"invalid_grant"});
  const digest=crypto.createHash("sha256").update(verifier).digest("base64url");
  if(digest!==item.challenge)return res.status(400).json({error:"invalid_grant"});
  oauthCodes.delete(code);
  const accessToken="mcp_"+crypto.randomUUID().replace(/-/g,"");
  oauthTokens.set(accessToken,{expiresAt:Date.now()+60*60*1000,clientId:item.clientId,scope:item.scope||"mcp"});
  res.json({access_token:accessToken,token_type:"Bearer",expires_in:3600,scope:item.scope||"mcp"});
 }catch{res.status(400).json({error:"invalid_request"})}
});
function oauthTokenValid(token){
 const item=oauthTokens.get(token);
 if(!item)return false;
 if(item.expiresAt<Date.now()){oauthTokens.delete(token);return false}
 return item.scope.split(/\s+/).includes("mcp");
}

function authorizeMcpRequest(req){
 const auth=String(req.get("authorization")||"");
 const bearer=auth.startsWith("Bearer ")?auth.slice(7).trim():"";
 if(bearer && oauthTokenValid(bearer))return true;
 const expected=String(process.env.CHAT_AGENT_KEY||"").trim();
 const supplied=String(req.get("x-agent-key")||req.query?.key||bearer).trim();
 if(!expected||!supplied)return false;
 const a=Buffer.from(supplied);
 const b=Buffer.from(expected);
 return a.length===b.length && crypto.timingSafeEqual(a,b);
}

function buildMcpServer(){
 const server=new McpServer(
  {name:"one-life-long-slave-phone-agent",version:"1.0.0"},
  {capabilities:{tools:{}}}
 );

 server.registerTool(
  "phone_command",
  {
   description:"שלח פקודה טבעית בעברית לסוכן Android המחובר. לדוגמה: פתח Instagram, עבור למסך הבית, פתח Chrome.",
   inputSchema:z.object({
    text:z.string().min(1).max(2000).describe("הפקודה לביצוע בטלפון"),
    deviceId:z.string().optional().describe("מזהה מכשיר ספציפי; אם לא נמסר, הפקודה נשלחת לכל המכשירים המחוברים")
   })
  },
  async ({text,deviceId})=>{
   const clean=String(text).trim();
   const id=String(deviceId||"").trim();
   let targets=[];
   if(id){
    const token=remoteDevices.get(id);
    if(token)targets.push(token);
   }else{
    targets=[...remoteDevices.values()];
   }
   if(!targets.length){
    return {isError:true,content:[{type:"text",text:"אין כרגע מכשיר Android רשום ומחובר לסוכן."}]};
   }
   const commandId=crypto.randomUUID();
   for(const token of targets){
    const q=remoteQueue.get(token)||[];
    q.push({id:commandId,text:clean,createdAt:Date.now(),source:"mcp"});
    remoteQueue.set(token,q);
   }
   return {
    content:[{
     type:"text",
     text:`הפקודה נשלחה לסוכן Android. מזהה: ${commandId}. מכשירים: ${targets.length}.`
    }]
   };
  }
 );

 server.registerTool(
  "phone_status",
  {
   description:"בדוק אילו מכשירי Android רשומים וכמה פקודות ממתינות לכל מכשיר.",
   inputSchema:z.object({})
  },
  async ()=>{
   const devices=[...remoteDevices.entries()].map(([deviceId,token])=>({
    deviceId,
    queued:(remoteQueue.get(token)||[]).length
   }));
   return {
    content:[{
     type:"text",
     text:JSON.stringify({connectedDevices:devices.length,devices})
    }]
   };
  }
 );

 return server;
}

const mcpHandler=createMcpHandler(buildMcpServer);

app.all("/mcp",async(req,res)=>{
 if(!authorizeMcpRequest(req)){
  return res.status(401)
   .set("WWW-Authenticate",`Bearer realm="one-life-long-slave-mcp", resource_metadata="${OAUTH_ISSUER}/.well-known/oauth-protected-resource/mcp"`)
   .json({error:"unauthorized"});
 }
 try{
  const nodeHandler=toNodeHandler(mcpHandler);
  await nodeHandler(req,res,req.body);
 }catch(e){
  console.error("MCP_ERROR",e);
  if(!res.headersSent)res.status(500).json({error:"MCP server error"});
 }
});

app.get("/{*splat}",(req,res)=>res.sendFile(path.join(__dirname,"public","index.html")));
app.listen(PORT,()=>console.log(`Phone Agent listening on ${PORT}`));