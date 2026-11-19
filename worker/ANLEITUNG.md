# KI-Vermittler (Cloudflare Worker) einrichten

1. console.anthropic.com -> API Keys -> Schluessel erstellen. Unter Billing Guthaben aufladen und ein Ausgabenlimit setzen.
2. dash.cloudflare.com (kostenloses Konto) -> Workers & Pages -> Create -> Worker -> Deploy -> "Edit code".
3. Inhalt von worker.js komplett einfuegen, Deploy.
4. Settings -> Variables and Secrets -> Add -> Typ Secret, Name `ANTHROPIC_API_KEY`, Wert = Schluessel aus Schritt 1.
5. Die Adresse des Workers (https://....workers.dev) an den Entwickler geben.
