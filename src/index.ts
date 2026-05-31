export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    // Leite APK-Downloads zu GitHub Releases weiter
    if (url.pathname.startsWith('/releases/')) {
      const fileName = url.pathname.replace('/releases/', '');
      // Privates Repo: FynnJS/Wartezeiten-App - nutze den richtigen Release-Tag
      const githubUrl = `https://github.com/FynnJS/Wartezeiten-App/releases/download/wartezeiten-app-1.0/${fileName}`;
      
      try {
        const response = await fetch(githubUrl, {
          headers: request.headers,
        });
        
        if (!response.ok) {
          return new Response(`GitHub Download fehlgeschlagen: ${response.status} ${response.statusText}`, { status: response.status });
        }
        
        return response;
      } catch (error) {
        return new Response(`Download-Fehler: ${error}`, { status: 500 });
      }
    }

    // Nutze die normalen Assets für HTML, CSS, JS, etc.
    return env.ASSETS.fetch(request);
  },
};

