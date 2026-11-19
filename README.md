# AK VOD Calendar

Aplicație Android TV (Kotlin, Compose for TV) care arată ce episoade și filme apar în fiecare zi,
pe aplicațiile de streaming pe care le ai, cu ora României.

## Structură

| Modul | Ce conține |
|---|---|
| `core` | Kotlin pur (fără Android): modele, clienți TMDB și OMDb, calculul orei de lansare, filtre de conținut, regulile de calitate, listele de seriale și filme anticipate, repository-urile de calendar (seriale și filme). Are teste unitare. |
| `app` | Aplicația Android TV: calendarul de seriale și cel de filme (zi/săptămână), detaliile episodului și ale filmului, Setări, DataStore pentru setări, legarea dependențelor. |

## Cheia TMDB

Cheia nu se pune în repo. Variante, în ordinea priorității:

1. În aplicație: **Setări → Cheie TMDB** (cheie API v3 sau token v4).
2. La build, în `local.properties`: `TMDB_API_KEY=...`
3. În CI, ca secret de repo `TMDB_API_KEY`.

## Cheia OMDb (opțională, pentru notele IMDb)

Fără ea, pragurile se aplică pe notele TMDB. Cheie gratuită pe omdbapi.com (1.000 de cereri pe zi;
răspunsurile se țin în cache 24 de ore). Se pune la fel ca cheia TMDB: în **Setări → Cheie OMDb**,
în `local.properties` ca `OMDB_API_KEY=...` sau ca secret de repo `OMDB_API_KEY`.

## Calendarul de seriale

- **Zi** sau **Săptămână** (luni–duminică), cu săgeți, „Azi” și „Reîncarcă”.
- **Ce intră**:
  - serialele în curs trebuie să treacă pragurile din Setări: nota și voturile IMDb (dacă ai cheie OMDb și OMDb știe serialul), altfel nota și voturile TMDB;
  - serialele noi (sezonul 1) intră dacă sunt pe lista celor mult așteptate sau dacă trec deja pragurile; comutatorul din Setări le poate lăsa pe toate;
  - serialele pe care le urmărești intră mereu, cele ascunse niciodată;
  - categoriile ascunse (anime, turcești, indiene, coreene, chinezești, telenovele, soap, reality, talk show, știri, documentare, copii, animație) nu apar deloc.
- **„Doar aplicațiile mele”** lasă doar episoadele de pe aplicațiile bifate în Setări.
- **„Arată și ce nu trece pragurile”** afișează estompat ce a fost filtrat și de ce, ca să poți regla pragurile.
- **Detalii episod**: descriere, notă IMDb/TMDB, rețea, unde e disponibil în RO și US, „Urmăresc serialul” și „Ascunde serialul”. Deschiderea în aplicație vine în etapa 4.

### Lista „mult așteptate”

Trakt nu se folosește. Lista vine din TMDB: cele mai populare 40 de seriale care au premiera în următoarele 120 de zile
plus cele mai populare 40 care au avut premiera în ultimele 150 de zile (ca un serial nou să rămână pe listă tot primul sezon).
Popularitatea TMDB urmărește vizualizările, watchlist-urile și voturile de pe TMDB, deci prinde bine hype-ul.
Sursa e în spatele interfeței `AnticipatedShowsSource`, ca Simkl sau alt serviciu să poată fi adăugat în etapa 5.

## Calendarul de filme

Tabul **Filme**, implicit pe săptămână, cu aceleași săgeți, „Azi” și „Reîncarcă”. Patru tipuri de lansări, fiecare cu buton de pornit/oprit (și în Setări):

- **Streaming**: premiera pe o platformă (un original Netflix, un film care apare pe Disney+). Data e din țara setată pentru aplicație (RO sau US). Platformele care lansează peste tot deodată (Netflix, Disney+, Prime Video, Max, Apple TV+, Paramount+) apar și pe aplicația setată pe RO, chiar dacă TMDB are doar data din SUA. Ora: 00:00 ora Pacificului, adică 10:00 în România; pentru celelalte, 00:00 în țara aplicației.
- **Digital**: închiriere / cumpărare în SUA (Apple TV, Google TV, Prime Video), adică momentul în care filmul se poate vedea acasă. Ora: 00:00 ora New York-ului (07:00 în România).
- **Cinema SUA** și **Cinema România**, separat. Fără oră, doar ziua. Dacă există doar lansare limitată, apare marcată „limitat”.

Cum se decide dacă o lansare digitală e streaming: TMDB pune la data digitală o notă cu platforma („Netflix”, „Disney+”), sau filmul n-a rulat larg în cinema și e deja pe o platformă din catalog. Altfel e „Digital”. TMDB nu are date pentru momentul în care un film vechi ajunge pe o platformă (de ex. un film din cinema care apare pe Max după câteva luni), deci acelea nu apar în calendar; în detalii se vede unde e disponibil acum.

**Ce intră**:

- un film cu destule voturi trebuie să treacă pragurile pentru filme din Setări (implicit IMDb ≥ 6,5 cu 10.000 de voturi, altfel TMDB ≥ 6,5 cu 200 de voturi);
- un film încă fără destule voturi (în cinema, sau abia lansat pe streaming) intră doar dacă e printre cele mai așteptate: cele mai populare 60 de filme din TMDB care apar în următoarele 180 de zile plus cele mai populare 80 apărute în ultimele 270 de zile (sursa e în spatele interfeței `AnticipatedMoviesSource`, ca Simkl să poată fi adăugat mai târziu);
- filmele urmărite intră mereu, cele ascunse niciodată;
- categoriile ascunse se aplică și filmelor (anime, indiene, turcești etc.), plus **Filme TV** (filmele făcute pentru televiziune), ascunse implicit;
- **„Streaming doar pe aplicațiile mele”** lasă doar premierele de streaming de pe aplicațiile tale; cinema și digital rămân.

**Detalii film**: descriere, note, toate lansările (cinema SUA/RO, digital, streaming), unde e disponibil acum în RO și US, „Urmăresc filmul” și „Ascunde filmul”.

## Build

```bash
./gradlew :core:test          # teste
./gradlew :app:assembleDebug  # APK debug în app/build/outputs/apk/debug/
```

În GitHub: **Actions → Android → Run workflow** construiește APK-ul debug și îl atașează la rulare.
La fiecare PR se rulează testele și build-ul, fără să se publice APK-ul.

## Ora de lansare

TMDB dă doar data episodului, deci ora e estimată (marcată cu „≈”). Toate orele sunt în ora României.

Difuzarea originală:

- originalele de streaming (Netflix, Max, Disney+ etc.) apar la 00:00 ora Pacificului, adică 09:00–10:00 în România;
- serialele TV/cablu (inclusiv HBO) apar la 21:00 în țara de origine (un episod american de seară apare la 03:00–04:00 a doua zi în România).

Apoi contează aplicația ta și țara setată pentru ea (RO sau US):

- dacă aplicația e rețeaua serialului (Netflix pentru un original Netflix, Max pentru HBO) sau e un serial românesc pe o aplicație setată pe RO, apare în același moment, și pe RO, și pe US;
- dacă aplicația doar licențiază serialul (un serial NBC pe Peacock, unul CBS pe SkyShowtime):
  - pe US apare a doua zi dimineață (la 6 ore după difuzare, de ex. 21:00 ET → 03:00 ET, adică 10:00 în România);
  - pe RO apare la 12:00 ora României, în aceeași zi dacă difuzarea a fost înainte de prânz, altfel a doua zi.

## Etape

1. Schelet, setări, integrare TMDB ✅
2. Calendarul de seriale cu filtre și praguri (IMDb prin OMDb, liste „mult așteptate”) ✅
3. Filme: streaming, digital, cinema US/RO ✅
4. Deep linkuri și „Deschide cu…” (DebridStream, Wuplay, Nuvio, Stremio)
5. Sincronizare tracking (Simkl sau alt serviciu), notificări, canal pe ecranul Android TV
