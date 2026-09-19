# Pehmeä Expressive — katselmointi ja korjaukset 18.9.2026

**Uusin kierros 19.9.2026:** [toisen katselmoinnin raportti](REVIEW-UI-EXPRESSIVE-ROUND2.md) käsittelee katselmoinnin uudet 15 kohtaa ja niiden paikalliset korjaukset. Alla oleva raportti kuvaa 18.9. tilannetta; erityisesti kohtien 6.4, 6.6 ja 6.12 siirtopäätökset eivät enää kuvaa nykyistä koodia.

Kolme katselmoinnissa ilmoitettua P2-asetteluvirhettä toistettiin testeillä ja korjattiin. Yhteenvetohintojen numerot, sähkövalitsimien vaihtoehdot ja aurinkoajat mahtuvat nyt myös 320 dp:n näkymään. Korjaukset ovat paikallisessa `ui-pehmea-expressive`-haarassa. Fyysisen laitteen varmennus on edelleen avoin.

## Lähtötila ja rajaus

- HEAD ennen korjauksia: `a891e15`, pohja `main` / `f6615ee`; kahdeksan käyttöliittymäcommittia.
- Työn perustana olivat `katselmointipyyntö`, `docs/UI-EXPRESSIVE.md`, suunnitelman sitovat ehdot sekä käyttäjän välittämä katselmointi, jossa oli kolme P2-löydöstä.
- Aiemman katselmoinnin ilmoittama lähtötulos oli 122 testiä, 0 epäonnistumista, 6 ohitettua sekä lint 0 virhettä / 4 varoitusta. Tätä ei esitetä uutena lähtötilan kokonaisajona.
- Koodikorjaukset koskevat vain `ElectricityScreen.kt`:tä, `WeatherScreen.kt`:tä ja `ScreenTest.kt`:tä. Lisäksi laadittiin tämä raportti ja käyttäjän myöhemmällä nimenomaisella luvalla päivitettiin `jatkomuistio`.
- Ennen viimeistä handoff-pyyntöä kaikki yhdeksän suojattua sijainti-/handoff-tiedostoa vastasivat työn alun SHA-256-tiivisteitä. Käyttäjä valtuutti sen jälkeen jatkomuistio-päivityksen sekä commitit ja pushin, mutta kielsi laitepäivitykset. jatkomuistion aiempi sisältö säilytettiin historiassa ja se sisällytettiin dokumentaatiocommittiin. Kahdeksan muuta sijaintitiedostoa, mukaan lukien `docs/LOCATION-PERFORMANCE.md`, säilyvät muuttamattomina ja commitoimattomina.

## Korjatut löydökset

| Löydös | Tulos ja korjaus | Commit |
|---|---|---|
| P2: yhteenvetohinnasta katoaa viimeinen desimaali, 320 dp / fontti 1,25 | Testi epäonnistui alkuperäisellä koodilla: `-123,456` sisälsi 8 merkkiä mutta viimeinen näkyvä merkki päättyi kohtaan 7. Asettelu mittaa kaikkien kolmen hinnan vaatiman leveyden 14 sp:n pienennysrajalla ja huomioi laattojen välit sekä sisäreunukset. Laatat pinoutuvat, kun rinnakkainen asettelu ei riitä. | `bc6cf8e` |
| P2: sähkövalitsimet leikkaavat päivänvalinnan | Testit epäonnistuivat sekä 320 dp / fontti 1,0 että 411 dp / fontti 1,2: `Huomenna` päättyi kohtiin 5 ja 6 odotetun 8 sijaan. Päivä- ja aikavälivalitsimet pinoutuvat pisimmän vaihtoehtotekstin mitatun leveyden, solujen sisäreunusten ja käytettävän leveyden perusteella. | `75b33e3` |
| P2: aurinkolähde vie nousuajan tarvitseman tilan | 320 dp:n testi epäonnistui oletusfontilla: nousuaika ei mahtunut tekstiasettelussa yhdelle riville. Lähde siirtyy omalle riville, jos kellonaikojen ja lähteen yhteisleveys väleineen ei mahdu. Kellonajat ovat luonnollisen levyisiä, ja ne voivat tarvittaessa siirtyä eri riveille. | `03fa878` |

Jokaisesta korjauksesta kirjoitettiin ensin käyttäytymistä tarkistava testi ja havaittiin sen epäonnistuminen ennen korjausta. Hintatestin ensimmäisessä kirjoitusvaiheessa korjattiin myös testin kenttänimen kirjoitusvirhe; käännösvirhettä ei laskettu regressiotodisteeksi. Hintatestin tekstiasettelun tarkistus täsmennettiin ja ajettiin uudelleen alkuperäistä tuotantokoodia vasten ennen ensimmäistä korjauscommittia.

Testit käyttävät `GetTextLayoutResult`-tulosta: teksti on yhdellä rivillä, sitä ei lyhennetä ellipsillä, viimeinen näkyvä merkki säilyy ja piirretty rivi mahtuu tekstisoluun yhden pikselin pyöristystoleranssilla. Pelkkä semantiikkapuun täydellinen tekstiarvo ei siis riitä läpäisyyn.

## Katselmointipyynnön kohdat 6.1–6.13

Arvio erottaa paikallisesti tarkistetun toiminnan ja oikeaa laitetta edellyttävät asiat. P3-merkinnät alla ovat jäljelle jääviä käytettävyys- tai siivoushuomioita, eivät uusia todettuja tietojen katoamisia.

| Kohta | Arvio | Vakavuus | Havainto / päätös |
|---|---|---|---|
| 6.1 Muuttuvat fontit | Osittain | — | Fonttitiedostojen `fvar`-taulu tarkistettiin: Bricolage `wght` 200–800 ja `opsz` 12–96; käytetyt 800 ja 96 ovat sallittuja. Figtree `wght` 300–900 sisältää käytetyt 400/500/600/700. Käännös ja Robolectric API 35 onnistuvat. Kaikkien API 33–37 -laitteiden renderöintiä ei varmennettu. `ExperimentalTextApi`-opt-in jätettiin ennalleen; sen poistamisen tarpeellisuutta ei päätelty pelkästä onnistuneesta käännöksestä. |
| 6.2 Hinnan automaattinen pienennys | Vahvistettu | P2, korjattu | API kääntyy tämän projektin BOMilla ilman erillistä TextAutoSize-opt-inia. Pienin sallittu fonttikoko ei kuitenkaan takaa mahtumista. Yhteenvetohinnat korjattiin commitissa `bc6cf8e`; pitkä negatiivinen luku testataan 320 dp:llä kertoimilla 1,0 / 1,25 / 1,6. Nykyhinnan päälohkon pitkää lukua tarkasteltiin myös kuvista. |
| 6.3 Otsikkorivi paikkahaun aikana | Osittain | P3 | Sisältö piilotetaan semantiikasta, otsikkoriviä ei. Sijaintipillerillä ei ole klikkaustoimintoa ja päivitys on pois käytöstä haun aikana. Otsikon mahdollinen ylimääräinen TalkBack-kohdistus jää laitekokeeseen; kohdistuksen loukkua ei osoitettu. Piilotusehdotusta ei toteutettu tässä korjaussarjassa. |
| 6.4 `currentBand` / `topLevel` | Ei ongelmaa kooditarkistuksessa | — | Avaimet sisältävät hinnat, ajan, aikavälin ja ALV:n sekä varoituksille paikan, luvan ja valitun paikan. Nykyhintaa lasketaan myös sähkönäkymässä, mutta 96–192 rivin aineistosta ei löytynyt virheellistä tilaa tai osoitettua suorituskykyongelmaa. ViewModel-rajapintaa ei muutettu tämän poistamiseksi. |
| 6.5 Alapalkki | Osittain | P3 | `Role.Tab`, `selectableGroup`, valinta ja vähintään 56 dp korkeus säilyvät. 1,3-kertainen rajoitus on edelleen tietoinen kompromissi. 320 dp:n kuvassa passiivinen Varoitukset lyhenee näkyvästi muotoon `Varoit…`; koko nimi säilyy tekstisemantiikassa ja välilehti on käytettävissä. Täydellistä suuren fontin saavutettavuutta ei luvata. Jätettiin erilliseksi ulkoasuratkaisuksi. |
| 6.6 `MetricTile`-semantiikka | Osittain | P3 | Lähde ja arvo ovat yhdistetyssä rivissä, yksikkö erillisessä laatan otsikossa. Arvosta yksin ei voi päätellä esimerkiksi tuulen yksikköä. TalkBackin lukujärjestys ja yksikkökontekstin riittävyys pitää tarkistaa laitteella; niitä ei väitetä varmennetuiksi. |
| 6.7 Asetuslohkon avain ja sijainti | Ei ongelmaa kooditarkistuksessa | — | `settings()` kutsutaan kerran joko tyhjän tai hintoja sisältävän haaran sisällä; haarat ovat toisensa poissulkevia. Kytkinten tila tulee parametreista eikä lohkon paikallisesta muistista. Olemassa olevat testit kattavat ALV:n, ilmoituksen ja tyhjän sähkötilan käytön. Asetusten sijoitus kaavion jälkeen säilytettiin. |
| 6.8 Hintakaavio | Ei tietojen katoamista | — | Nykyjakson kellonaika säilyy pylvään sisältökuvauksessa. Sivuvieritys, pylvään valinta ja kiinteä akseli säilyvät testeissä. Kaavio alkaa edelleen indeksistä 0; alkuvieritys nykyhetkeen on valinnainen käytettävyysparannus, jota ei sisällytetty näihin kolmeen korjaukseen. |
| 6.9 Varoituslohkojen kontrasti | Ei ongelmaa laskennassa | — | Molempien teemojen kaikkien kolmen tason tekstit tarkistettiin lähdekoodin väreistä. Päätekstin pienin suhde 8,78:1, himmeämmän tekstin 5,76:1, korostuspillerin 6,79:1 ja aikapillerin 6,00:1. Aikapillerin tausta laskettiin 0,16-alfalla lohkon päälle. Kaikki ylittävät pyydetyn 4,5:1. |
| 6.10 Pienet värilohkotekstit | Ei kontrastiongelmaa laskennassa | — | FMI:n, Open-Meteon ja aurinkolohkon `muted` / `top` -suhteet molemmissa teemoissa ovat vähintään 4,90:1. Pienin on vaalean aurinkolohkon lähdeteksti. Fyysistä luettavuutta tai dynaamisen teeman kaikkia pintoja ei tällä laskennalla varmenneta. |
| 6.11 Poistetut näkyvät tekstit | Ei poistunutta perustoimintoa havaittu | — | Haun toiminto on sijaintipillerissä ja sen klikkauskuvauksessa. Hinta-alue Suomi näkyy otsikossa, aurinkoajoilla on nousu-/laskukuvaus ja varoituksilla ikoni sekä nimetty taso. Sovellusnimeä, erillisiä lähdeotsikoita tai aiempia ohjetekstejä ei palautettu. |
| 6.12 Käyttämättömät jäänteet | Vahvistettu, siivous | P3 | `WeatherScreen.onOpenPlacePicker` jäi käyttämättömäksi ja `ForecastColors.bottom` on edelleen rakenteessa ilman nykyisiä kuluttajia. Ne eivät muuta toimintaa. Siivousta ei yhdistetty mahtumiskorjauksiin. `PriceColors.bottom` on edelleen käytössä eikä ole sama jäänne. |
| 6.13 Esikatselut | Osittain | — | Debug-esikatselut kääntyvät ja käyttävät samaa AppScreen-kehystä. Robolectric-kuvia tarkasteltiin, myös korjattuja 320 dp:n näkymiä ja 411 dp / 1,2 -valitsimia. Android Studion interaktiivista Preview-näkymää ei avattu. |

API-arvion tukena olivat Androidin [TextAutoSize-viite](https://developer.android.com/reference/kotlin/androidx/compose/foundation/text/TextAutoSize) ja [muuttuvien fonttien ohje](https://developer.android.com/develop/ui/compose/text/fonts#variable-fonts). Ne täydentävät paikallista käännös- ja testitulosta, eivät osoita laitteen renderöintiä.

## Kohdan 7 ehdotukset

- **7.4 toteutettu:** 320 dp:n regressiot hinnalle, valitsimille ja aurinkotiedoille. Valitsimilla lisäksi 411 dp:n kohtalainen fonttiskaalaus.
- **7.1 alkuvieritys**, **7.2 otsikon semantiikan piilotus** ja **7.3 alapalkin uusi suuren fontin ratkaisu** jätettiin ennalleen edellä kuvatuin perustein. Niitä ei merkitä korjatuiksi tai laitteella varmennetuiksi.

## Varmennus

Testiajot tehtiin yksi kerrallaan odottaen kunkin valmistuminen; Gradle-ajoja ei käynnistetty rinnakkain eikä taustaprosesseina.

Ennen jokaista korjauscommittia:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests fi.omasaasahko.ThemeTest --tests fi.omasaasahko.ScreenTest --console=plain
```

Ensimmäisen korjauksen jälkeen 21 testiä, toisen jälkeen 23 ja kolmannen jälkeen 24; jokaisessa commitia edeltävässä ajossa 0 epäonnistumista.

Lopuksi:

```powershell
.\validate.ps1
```

**BUILD SUCCESSFUL: 126 testiä yhteensä, 120 läpi, 0 epäonnistumista / virhettä, 6 live-testiä ohitettu. Lint: 0 virhettä, 4 varoitusta. Debug-APK rakentui.** Uusia testimetodeja on neljä. Verkkotestien valintoja ei otettu käyttöön.

Ajo tehtiin nykyisessä työpuussa, jossa käyttäjän commitoimattomat sijaintikorjaukset ja niiden testit ovat mukana. Lukua 126 ei pidä esittää puhtaan GitHub-checkoutin testimääränä; sijaintikorjauksia ei sisällytetty tämän työn committeihin. Ennen dokumentaatiocommittia myös 24 käyttöliittymätestin ajo uusittiin onnistuneesti.

Lisävarmennukset:

- `AppScreen(...)`-allekirjoitus vastaa `main`-haaraa.
- Yhtään vanhaa `testTag("...")`-arvoa ei kadonnut vertailussa `main`-haaraan.
- `ui`-paketissa ei ole `Brush.`- tai `BorderStroke`-osumia.
- Korjauksissa ei muutettu riippuvuuksia, dataa, ViewModeleita, ilmoituksia, versionumeroa tai dynaamisen teeman oletusta.
- Uudet kuvat ovat kansiossa `app/build/screenshots/`: `electricity-summary-320-*`, `electricity-selectors-320-*`, `electricity-selectors-411-*` ja `weather-sun-320-*`.
- Aurinkotesti kattaa FMI:n ja Open-Meteon, vaalean ja tumman teeman sekä fonttikertoimet 1,0 / 1,25 / 1,6. Valitsintestit tarkistavat myös valintojen toiminnan ja 44 dp:n vähimmäiskorkeuden.

## Ennen yhdistämistä: oikean puhelimen debug-koe

Puhelimeen ei tässä työssä asennettu mitään. Testaa erikseen:

1. Dynaaminen väri vaaleassa ja tummassa teemassa sekä merkitysvärien kontrastit.
2. Muuttuvien fonttien ulkoasu, pitkät hinnat ja 1,6-kertainen fontti oikealla renderöijällä.
3. Eleohjauksen ja kelluvan alapalkin väli, kosketuskohteet ja näppäimistön vaikutus paikkahakuun.
4. Vedä-päivittääksesi-ele, lataustila ja paikkahaun avaaminen/sulkeminen.
5. TalkBackin välilehdet, paikkahaun kohdistus, otsikkorivin ylimääräiset kohteet, mittarien yksiköt ja aurinkoaikojen kuvaukset.

Käyttäjä pyysi työn lopuksi commitit ja pushin GitHubiin sekä muistimerkinnän ja jatkomuistio-päivityksen. Toimitushaara on `origin/ui-pehmea-expressive`. Main-haaraan yhdistäminen, version nosto, release-APK ja laitepäivitykset eivät kuulu toimitukseen. Käyttäjä katselmoi koodin katselmoinnin kanssa 19.9.2026 ja toimittaa mahdolliset uudet löydökset. Paikallinen debug-varmennus ei korvaa laitekokeita.
