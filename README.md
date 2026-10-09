# Sää & Sähkö

Android-sovellus, joka näyttää Ilmatieteen laitoksen ja Open-Meteon sääennusteet rinnakkain, Suomen pörssisähkön hinnat ja FMI:n säävaroitukset. Käyttöliittymä on suomeksi, ruotsiksi ja englanniksi. Toteutettu Kotlinilla, Jetpack Composella ja Material 3:lla.

**Vaatimukset:** Android 13 tai uudempi (API 33). Kohdealusta Android 17 (API 37). Versio 0.3.0.

**Rekisteröity Googlelle.** Paketin nimi ja allekirjoitusavain on rekisteröity Googlen Android Developer Consoleen, joten sovellus asentuu jatkossakin normaalisti, kun Googlen uudet [sivulataussäännöt](https://developer.android.com/developer-verification) tulevat voimaan.

## Ominaisuudet

- **Sää.** FMI ja Open-Meteo rinnakkain: nykyhetki, 24 tunnin vertailu (lämpötila 0,1 °C:n tarkkuudella, tuuli, sade ja sateen riski), sademääräkaavio sekä viikon ennuste avattavine tunteineen. Lähimmän FMI-aseman tuore havainto ja auringon nousu- ja laskuajat.
- **Sähkö.** Tämän ja huomisen päivän varttihinnat ja tuntikeskiarvot, päivän tilastot, vieritettävä kaavio ja viisi hintaluokkaa väreineen. ALV 25,5 % -kytkin, hinnat kolmella desimaalilla. Valinnainen ilmoitus huomisen hinnoista, kun ne on julkaistu (taustahaku klo 14–16).
- **Varoitukset.** Sijaintiin osuvat FMI:n keltaiset, oranssit ja punaiset varoitukset viideksi päiväksi, alue ratkaistaan virallisista CAP-polygoneista. Valinnaiset taustailmoitukset 15, 30 tai 60 minuutin välein.
- **Paikka.** Puhelimen sijainti tai Maanmittauslaitoksen paikkahaku ja suosikit (enintään 50).
- **Kielet.** Suomi, ruotsi ja englanti puhelimen kielen mukaan: suomi ja ruotsi omalla kielellään, muut kielet englanniksi. Kielen voi vaihtaa sovelluskohtaisesti Androidin asetuksista. Kellonajat, päivämäärät ja desimaalierotin seuraavat kieltä, FMI:n varoitustekstit ja Maanmittauslaitoksen paikannimet haetaan sovelluksen kielellä. Tarkemmin: [docs/LOCALIZATION.md](docs/LOCALIZATION.md).
- **Ulkoasu.** Vaalea ja tumma teema sekä puhelimen Material You -värit. Tekstien kontrasti nostetaan WCAG AAA -tasolle (7:1) ja kuvakkeiden vähintään 3:1:een myös puhelimen omilla väreillä, jotta näkymä erottuu kirkkaassa auringonvalossa.
- **Yksityisyys.** Ei käyttäjätiliä, analytiikkaa, taustapaikannusta eikä omaa palvelinta. Koordinaatit lähetetään vain FMI:lle, Open-Meteolle ja MML:lle hakuja varten. Välimuisti pysyy sovelluksen omassa tallennustilassa.

## Tietolähteet

- [Ilmatieteen laitoksen avoin data](https://www.ilmatieteenlaitos.fi/avoin-data): WFS-ennusteet ja -havainnot, aurinkoajat ja CAP-varoitukset (CC BY 4.0).
- [Open-Meteo Forecast API](https://open-meteo.com/en/docs) (CC BY 4.0).
- [Elering](https://dashboard.elering.ee/assets/swagger-ui/index.html): Nord Poolin Suomen hinta-alueen hinnat (€/MWh muunnetaan snt/kWh-hinnaksi).
- [Maanmittauslaitoksen geokoodauspalvelu](https://www.maanmittauslaitos.fi/kartat-ja-paikkatieto/aineistot-ja-rajapinnat/paikkatietojen-rajapintapalvelut/geokoodauspalvelu) ja paikannimirekisteri (CC BY 4.0). Vaatii oman API-avaimen.
- Fontit Bricolage Grotesque ja Figtree (SIL Open Font License, [docs/licenses](docs/licenses)).

## Kääntäminen

Tarvitaan JDK 21 ja Android SDK 37. MML-avain annetaan Gitin ulkopuolisessa `local.properties`-tiedostossa (`MML_API_KEY=...`) tai samannimisessä ympäristömuuttujassa. Ilman avainta paikkahaku ilmoittaa virheestä, muu sovellus toimii.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

Debug-APK syntyy polkuun `app/build/outputs/apk/debug/app-debug.apk`. `validate.ps1` ajaa saman tarkistuksen. Allekirjoitettu julkaisu rakennetaan `build-release.ps1`-skriptillä, katso [RELEASE.md](RELEASE.md).

## Testit

Yksikkö- ja käyttöliittymätestit ajetaan Robolectricilla ilman laitetta; kuvakaappaukset tallentuvat hakemistoon `app/build/screenshots`. Verkkoa käyttävät testit ohitetaan oletuksena ja otetaan käyttöön ympäristömuuttujilla `SAA_LIVE_API_TESTS=1` (rajapinnat ja välimuisti) sekä `SAA_PRICE_CHECK=1` (Nord Pool -vertailu).

```powershell
$env:SAA_LIVE_API_TESTS = '1'
.\gradlew.bat :app:testDebugUnitTest --tests '*RepositoryLiveTest' --rerun-tasks --console=plain
```

## Rakenne

- `domain`: ennuste- ja hintamallit, aikajaksot ja BigDecimal-hintalaskenta.
- `data`: FMI-, Open-Meteo-, Elering- ja MML-haut, atominen välimuisti, varoitusten tulkinta.
- `ui`: teema ja kontrastin varmistus, piirretyt sääsymbolit, sää-, sähkö- ja varoitusnäkymät.
- `res/values`, `values-fi`, `values-sv`: käyttöliittymän tekstit englanniksi (oletus), suomeksi ja ruotsiksi.
- `AppViewModel` ja `WarningsViewModel`: tilat, päivitykset ja WorkManager-taustahaut.

Tarkemmat kuvaukset: [sään ja varoitusten varmennus](docs/WEATHER-VERIFICATION.md), [hintavarmennus](docs/PRICE-VERIFICATION.md), [käyttöliittymä](docs/UI-EXPRESSIVE.md) ja [paikannus](docs/LOCATION-0.2.2.md).

## Lisenssi

[Apache License 2.0](LICENSE).
