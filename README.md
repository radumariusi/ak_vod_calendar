# AK VOD Calendar

Aplicație Android TV (Kotlin, Compose for TV) care arată ce episoade și filme apar în fiecare zi,
pe aplicațiile de streaming pe care le ai, cu ora României.

## Structură

| Modul | Ce conține |
|---|---|
| `core` | Kotlin pur (fără Android): modele, client TMDB, calculul orei de lansare, filtre de conținut, repository-ul de calendar. Are teste unitare. |
| `app` | Aplicația Android TV: ecranele Azi și Setări, DataStore pentru setări, legarea dependențelor. |

## Cheia TMDB

Cheia nu se pune în repo. Variante, în ordinea priorității:

1. În aplicație: **Setări → Cheie TMDB** (cheie API v3 sau token v4).
2. La build, în `local.properties`: `TMDB_API_KEY=...`
3. În CI, ca secret de repo `TMDB_API_KEY`.

## Build

```bash
./gradlew :core:test          # teste
./gradlew :app:assembleDebug  # APK debug în app/build/outputs/apk/debug/
```

În GitHub: **Actions → Android → Run workflow** construiește APK-ul debug și îl atașează la rulare.
La fiecare PR se rulează testele și build-ul, fără să se publice APK-ul.

## Ora de lansare

TMDB dă doar data episodului, deci ora e estimată (marcată cu „≈”):

- originalele de streaming (Netflix, Max, Disney+ etc.) apar la 00:00 ora Pacificului, adică 09:00–10:00 în România;
- serialele TV/cablu apar la 21:00 în țara de origine (un episod american de seară apare la 03:00–04:00 a doua zi în România).

Data se ia după țara setată pentru aplicația pe care o ai (RO sau US). Diferențele pe regiuni se rafinează în etapa 2.

## Etape

1. Schelet, setări, integrare TMDB ✅
2. Calendarul de seriale cu filtre și praguri (IMDb prin OMDb, liste „mult așteptate”)
3. Filme: streaming, digital, cinema US/RO
4. Deep linkuri și „Deschide cu…” (DebridStream, Wuplay, Nuvio, Stremio)
5. Sincronizare tracking (Simkl sau alt serviciu), notificări, canal pe ecranul Android TV
