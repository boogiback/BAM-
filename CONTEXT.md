# 📌 BAM! – CONTEXT

## קבצים עיקריים 📂
- `AndroidManifest.xml`  
  כולל הרשאות (INTERNET, FOREGROUND_SERVICE, POST_NOTIFICATIONS) ומגדיר את ה־Activities וה־Service.

- `MainActivity.java`  
  מציג את הלוגים שנשלחים דרך Webhook (בגרסה זו גם UI חדש + deep link).

- `UploadService.java`  
  Foreground Service שמעלה את התמונות ושולח התראות.

- `ShareBridgeActivity.java`  
  מקבל קבצי Intents מסוג תמונה.

- `ViewDataActivity.java`  
  מציג את ה־JSON שהתקבל (גרסה מעודכנת עם עיצוב מתוקן, deep link וכו’).

---

## גרסאות 📜

### v3.1
- עדכון מיתוג: שינוי שם האפליקציה ל־**BAM!**
- הסרת כתובת ה־Webhook מה־Toolbar
- כל Entry בלוגים במסך הראשי הפך ללחיץ, פותח את **ViewDataActivity** באמצעות deep link (`myapp://viewdata`)
- תיקון תצוגה ב־Dark Mode (בעיקר טקסטים שלא נראו)
- כיבוד Status bar ו־Navigation bar (אין יותר רקע סגול עליון, ואין חפיפה עם כפתורים תחתונים)
- **ViewDataActivity**: נוספו רווחים בין הכפתורים ותוקנה חפיפה עם ניווט תחתון
- הוספת אייקון `ic_menu.xml` ומשאבי צבעים (`purple_200` וכו’)
- עדכון `strings.xml` (כולל app_name ל־BAM!)

### v3.0
- הוספת מסך ראשי המציג את כל ה־JSON שהתקבלו (עם timestamp והפרדה בין entries).
- תפריט המבורגר עם אפשרות לשנות Webhook (דיפולט: https://hook.eu2.make.com/3whnaefpngyp1nwsa3ht1vt9pob17reg).
- כפתור Clear Logs לניקוי רשומות.
- הוספת ViewDataActivity להצגת תוצאה מלאה כולל JSON.
- חיבור בין UploadService להתראות עם deep link ל־ViewDataActivity.

---

## מה נשאר לשלב הבא 🚀
1. הוספת אפשרויות נוספות לתפריט (Settings וכו’).
2. שיפור מבנה הנתונים במסך הראשי (הצגה מסודרת של השדות מה־JSON).
3. תמיכה ב־Persistence יותר מלאה (שמירה ל־DB או קובץ).  
