# KI-Vermittler (Cloudflare Worker) einrichten

Gemeinsam fuer beide Varianten:
1. dash.cloudflare.com (kostenloses Konto) -> Workers & Pages -> Create -> Worker -> Deploy -> "Edit code".
2. Inhalt von worker.js komplett einfuegen, Deploy.

## Variante A: KOSTENLOS (Cloudflare Workers AI)
3. Worker -> Settings -> Bindings -> Add -> "Workers AI", Variablenname exakt `AI`. Deploy.
   (Kein Schluessel noetig. Es gilt ein taegliches Gratis-Kontingent; Fotos werden nicht gelesen.)

## Variante A2: KOSTENLOS mit Google Gemini (besser, liest Fotos)
3. aistudio.google.com -> Get API key -> Schluessel erstellen.
4. Worker -> Settings -> Variables and Secrets -> Secret `GEMINI_API_KEY` = Schluessel. Deploy.
   (Vorrang: ANTHROPIC_API_KEY > GEMINI_API_KEY > Workers AI. Nur Secret, niemals in den Chat!)

## Variante B: Claude (bezahlt, besser)
3. console.anthropic.com -> API Keys -> Schluessel erstellen, Guthaben aufladen, Ausgabenlimit setzen.
4. Worker -> Settings -> Variables and Secrets -> Secret `ANTHROPIC_API_KEY` = Schluessel.
   Ist dieses Secret gesetzt, hat es Vorrang vor Workers AI.

Danach: Adresse des Workers (https://....workers.dev) an den Entwickler geben.
