import express from "express";
import path from "path";
import { fileURLToPath } from "url";

const app = express();
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PORT = process.env.PORT || 3000;

app.use(express.json({ limit: "1mb" }));
app.use(express.static(path.join(__dirname, "public")));

const SYSTEM = `אתה סוכן אישי בעברית. אתה מתכנן פעולות שימושיות בטלפון, אבל האתר יכול לבצע רק פעולות שהדפדפן/Android מאפשרים.
ענה תמיד ב-JSON תקין בלבד:
{"reply":"תשובה קצרה בעברית","actions":[{"type":"open_url","url":"https://example.com","label":"פתיחה"}]}
סוגי actions מותרים:
open_url עם https URL
open_app עם package ו-label עבור אפליקציות Android נפוצות בלבד
dial עם number
sms עם number ו-text
email עם address ו-subject ו-body
maps עם query
copy עם text
לפעולה שאינה אפשרית בדפדפן, החזר actions=[] והסבר קצר ב-reply.
אל תבקש API key מהמשתמש. אל תחשוף סודות. אל תמציא ביצוע פעולה שלא בוצעה.`;

app.post("/api/chat", async (req, res) => {
  try {
    const key = process.env.GROQ_API_KEY;
    if (!key) return res.status(503).json({ error: "השרת עדיין לא מחובר ל-GROQ_API_KEY" });

    const messages = Array.isArray(req.body?.messages) ? req.body.messages.slice(-20) : [];
    const response = await fetch("https://api.groq.com/openai/v1/chat/completions", {
      method: "POST",
      headers: { "Authorization": `Bearer ${key}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        model: "openai/gpt-oss-20b",
        temperature: 0.2,
        messages: [{ role: "system", content: SYSTEM }, ...messages]
      })
    });
    const data = await response.json();
    if (!response.ok) return res.status(response.status).json({ error: data?.error?.message || "שגיאת Groq" });

    const raw = data?.choices?.[0]?.message?.content || '{"reply":"לא התקבלה תשובה","actions":[]}';
    let parsed;
    try { parsed = JSON.parse(raw); }
    catch { parsed = { reply: raw, actions: [] }; }
    res.json(parsed);
  } catch (e) {
    res.status(500).json({ error: "שגיאת שרת" });
  }
});

app.get("*", (req, res) => res.sendFile(path.join(__dirname, "public", "index.html")));
app.listen(PORT, () => console.log(`Phone Agent listening on ${PORT}`));
