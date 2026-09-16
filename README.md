# Sää & Sähkö

Henkilökohtainen, suomenkielinen Android-sovellus. Säävertailu ja Suomen pörssisähkö samassa sovelluksessa. Toteutettu Kotlinilla, Jetpack Composella ja Material 3:lla. Kohde Android 17 / API 37; tekninen minimiversio Android 13 / API 33.

## Käyttö

- Sovellus avautuu Sää-välilehdelle. Yläpalkin **Sää | Pörssi-sähkö | Varoitukset** vaihtaa näkymää.
- Sää käyttää nykyistä sijaintia vain sovelluksen ollessa käytössä. Myös likimääräinen sijaintilupa riittää. Lupaa pyydetään käyttäjän painaessa **Salli sijainti**.
- Ilmatieteen laitos ja Open-Meteo näkyvät rinnakkain: nykyhetken ennuste, 24 tunnin vertailu, sademääräkaavio ja avattava viikon ennuste. FMI:n lähialueen havainto esitetään erillisenä, kun se on saatavilla.
- Sääkortit ovat aina yhtä korkeat myös puuttuvilla tiedoilla. FMI:n sininen ja Open-Meteon turkoosi kortti sekä moniväriset sääsymbolit mukautuvat tummaan/vaaleaan teemaan. Viikon päivät avautuvat nuolesta. Jokaisen viikkokortin sarakkeissa näkyvät nimet Ilmatieteen laitos ja Open-Meteo.
- Sijainnin ja päivämäärän vieressä näkyy **Nyt HH.mm**. Korttien **Ennuste HH.mm** kertoo erikseen ennusteen ajankohdan. Auringonnousu ja -lasku tulevat ensisijaisesti FMI:ltä, tarvittaessa Open-Meteolta; lähde näkyy aurinkokortissa.
- Sähkösivulla ovat tämä päivä ja huominen, vartit ja tuntikeskiarvot, päivän tilastot, vaakasuunnassa vieritettävä kaavio sekä täydellinen aikaluettelo. Kaavion pylväät ovat 44 dp leveät (kosketusalue vähintään 52 dp), hinta-asteikko pysyy vasemmalla ja kellonajat vierivät pylväiden mukana.
- **ALV 25,5 %** -kytkin vaihtaa kaikkien sähköhintojen, tilastojen ja kaavion verollisen/verottoman esityksen. ALV on oletuksena päällä. Hinnat näytetään aina kolmella desimaalilla, esimerkiksi **6,125 snt/kWh**. Hintaan ei sisälly myyjän marginaalia tai sähkönsiirtoa.
- Vartti/tunti- ja ALV-valinnat muistetaan. Tumma/vaalea tila ja käyttöliittymän korostusvärit seuraavat puhelinta.
- Sähkön hintavärit: alle 5,000 vihreä, 5,000–9,999 keltainen, 10,000–14,999 oranssi, 15,000–19,999 punainen ja vähintään 20,000 violetti (snt/kWh). Väri ja sanallinen hintataso seuraavat näytettävää hintaa, ALV-valintaa sekä vartti/tuntivalintaa. Sama asteikko koskee nykyhintaa, tilastoja, kaaviota ja hintalistaa. Negatiivinen hinta on vihreä, puuttuva tieto neutraali. Rajat perustuvat käyttäjän valintoihin, eivät sähkömarkkinan viralliseen luokitukseen.
- Viimeisimmän hintahaun ajankohta näkyy sähkösivun yläosassa. Kaavion alla on väriselite, ja valittu pylväs säilyttää hintavärinsä.
- Oma aurinko–pilvi–salama-kuvake sisältää värillisen adaptiivisen kuvakkeen sekä erillisen yksivärisen version Androidin teemakuvakkeille.
- Päivitys vetämällä alaspäin tai **Päivitä**-painikkeesta. Automaattinen päivitys sovelluksen ollessa käytössä 15 minuutin välein. Näytön kello ja voimassa oleva hintajakso päivittyvät 30 sekunnin rajoilla; päivän vaihtuessa haetaan hinnat uudelleen.
- Verkkokatkon aikana käytetään viimeksi onnistuneesti tallennettuja tietoja niiden aikaleimoineen. Eri paikan ennusteita ei sekoiteta. Puuttuva hinta tai sääarvo näytetään viivana.

## Säävaroitukset

Sovelluksen Varoitukset-välilehti näyttää koordinaatteihin osuvat keltaiset, oranssit ja punaiset varoitukset, myös kaikki syötteessä julkaistut tulevat päivät. Päiväriviltä voi valita Kaikki tai yksittäisen päivän FMI:n viiden vuorokauden jaksolta. Alue ratkaistaan virallisista CAP-polygoneista: Tikkurila kuuluu Uudenmaan varoitusalueeseen, mutta pienempi varoitusalue ei automaattisesti laajene koko maakuntaan.

Ilmoitukset otetaan käyttöön sivun kytkimellä ja Androidin ilmoitusluvalla. Oletustarkistusväli on 30 minuuttia, vaihtoehdot 15/30/60 minuuttia. Android voi viivästyttää taustahakua. Kyse on paikallisista ilmoituksista taustahaun jälkeen, ei palvelimen välittömästä pushista. GPS:ää ei käytetä taustalla. Matkalla sovellus pitää avata, jotta paikka vaihtuu; paikka ja hakuaika näkyvät sivulla. Sama samansisältöinen varoitus ei ilmoita jatkuvasti uudelleen.

[Sään ja varoitusten virallisten lähteiden varmennus](docs/WEATHER-VERIFICATION.md) sisältää korjatut sääsymbolit, sadejaksojen aikarajat, FMI:n CAP-sopimuksen sekä testit.

## Huomisen hintailmoitus

Sähkösivun **Ilmoita huomisen hinnat** -kytkin ottaa ilmoituksen käyttöön. Ilmoitus lähetetään kerran toimituspäivää kohti vasta, kun kaikki Suomen seuraavan päivän vartit ovat saatavilla (92/96/100). Siinä näkyvät päivän keskihinta ja halvin/kallein tunti kolmella desimaalilla, ALV-valintasi mukaisesti. Ilmoituksen painaminen avaa oikean toimituspäivän hinnat.

Taustatarkistus 30 minuutin välein, ei sijaintilupavaatimusta. Android voi viivästyttää tarkistusta. Kun ilmoitus on lähetetty, tämän toiminnon verkkohakuja ei enää tehdä samalle päivälle. Julkaisu tunnistetaan datasta, ei kiinteästä kellonlyömästä. Säävaroituksilla ja sähköhinnoilla on erilliset kytkimet ja ilmoituskanavat.

## Kääntäminen ja testaus

Tarvitaan JDK 21, Android SDK 37 ja Android build tools. Määritä `ANDROID_HOME` tai paikallinen, versionhallinnasta ohitettu `local.properties` (`sdk.dir=...`).

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Sovellustunnus `fi.omasaasahko`, versio 0.2.0 (`versionCode=4`). Debug käyttää paikallista debug-allekirjoitusta. Pysyvästi allekirjoitettu release löytyy projektin juuresta: `Saa-Sahko-0.2.0-release.apk`. [Release ja Googlen rekisteröintitiedot](RELEASE.md).

Erikseen ajettava integraatiotesti tekee oikeat verkkopyynnöt Vantaan julkiselle vertailusijainnille ja tarkistaa sovelluksen omien parsereiden sekä välimuistin toiminnan:

```powershell
$env:SAA_LIVE_API_TESTS = '1'
.\gradlew.bat :app:testDebugUnitTest --tests '*RepositoryLiveTest' --rerun-tasks --console=plain
Remove-Item Env:SAA_LIVE_API_TESTS
```

Tavallinen testiajo ohittaa verkkointegraatiotestin. Käyttöliittymätestit toimivat paikallisesti Robolectricilla ja tuottavat kuvat hakemistoon `app/build/screenshots`. Android Studion Compose-esikatselut ovat debug-lähdekoodissa. Esimerkkidataa ei käytetä varsinaisessa sovelluksessa.

Erillinen Nord Pool -vertailu hakee Elering-hinnat sovelluksen omalla Repositorylla ja vertaa jokaisen FI-vartin Nord Poolin julkiseen dataportaaliin. Lisäksi se tarkistaa sovelluksen vartti- ja tuntinäytöt ALV päällä/pois sekä Suomen päivänrajat. Nord Poolin toimituspäivät käsitellään Keski-Euroopan aikavyöhykkeellä. Huomisen tiedot tarkistetaan, jos ne on julkaistu. Hintojen tai saatavilla olevien jaksojen ero aiheuttaa testin epäonnistumisen.

```powershell
$env:SAA_PRICE_CHECK = '1'
.\gradlew.bat :app:testDebugUnitTest --tests '*OfficialPricesLiveTest' --rerun-tasks --console=plain
Remove-Item Env:SAA_PRICE_CHECK
```

[Hintavarmennus 16.9.2026](docs/PRICE-VERIFICATION.md): 192 varttihintaa täsmäsi, 480 hintanäyttöä tarkistettiin. Tämä vertailu on erikseen ajettava testi, ei sovelluksen jokaisen haun yhteydessä tehtävä ristiintarkistus. Sovelluksen varsinainen hintalähde pysyy Eleringissä; Nord Poolin dataportaalin saatavuus tai rajapintamuutos ei estä sovelluksen käyttöä.

## Rakenne ja tietojen käsittely

- `domain`: yhteiset ennustemallit, kellonajat, hintajaksot ja BigDecimal-hintalaskenta.
- `data`: FMI/WFS-, Open-Meteo- ja Elering-haut, paikkatieto sekä atomisesti kirjoitettava, sovelluksen sisäinen välimuisti.
- `AppViewModel`: itsenäiset sääpalvelut, päivitys- ja peruutuskäytös, sijainnin vaihdot ja etualan ajastus.
- `ui`: teema, piirretyt sääsymbolit, säävertailu, sähkösivu ja varoitussivu.
- `WarningService` / `WarningsViewModel`: FMI CAP -syöte, atominen välimuisti, aluekohdistus, ilmoitusten toistojen esto ja WorkManager-taustahaku.

Eleringin veroton €/MWh-hinta muunnetaan snt/kWh-hinnaksi jakamalla kymmenellä. ALV-kytkimen ollessa päällä se kerrotaan lisäksi luvulla 1,255. Tuntikeskiarvo lasketaan neljästä pyöristämättömästä vartista. Laskennassa käytetään desimaalilukuja; näyttöpyöristys on HALF_UP. Puutteellista tuntia ei tulkita täydeksi tunniksi eikä puutteelliselle päivälle lasketa päivän keskihintaa. Negatiiviset hinnat säilyvät. Välimuistissa säilytetään alkuperäiset verottomat hinnat, joten verovalinta ei vaadi uutta verkkohakua.

Sähkön aikajaksot muodostetaan UTC-aikaleimoista ja Suomen aikavyöhykkeen päivänrajoista. Kevään päivässä on 92 varttia / 23 tuntia ja syksyn päivässä 100 varttia / 25 tuntia. Toistuvat kellonajat erotetaan UTC-poikkeamalla. Sähkö käyttää aina Suomen aikaa; sää näyttää ennustepaikan aikavyöhykkeen, kun Open-Meteon vastaus on saatavilla.

FMI:n havaintoja haetaan rajatulta alueelta, ja niistä valitaan paikan lähin lämpötilahavainnon palauttava asema. Asemien arvoja ei yhdistetä. Yli 90 minuuttia vanhaa havaintoa ei näytetä nykyhavaintona. FMI:n `FeelsLike` tulee suoraan ennusteesta. FMI:n puuttuvaa sadeprosenttia tai UV-arvoa ei täytetä Open-Meteon tiedoilla. Osittainen ennustepäivä merkitään, eikä puuttuvista tunneista lasketa sadepäiväsummaa.

Paikkatieto lähetetään ennustehakua varten FMI:lle ja Open-Meteolle. Paikannimen selvittää Androidin Geocoder. Ei käyttäjätiliä, analytiikkaa, taustapaikannusta eikä omaa palvelinta. Valinnaiset varoitusilmoitukset tarkistetaan taustalla viimeksi haetulle sijainnille. Sijaintia sisältävä välimuisti jää sovelluksen omaan tallennustilaan; varmuuskopiointi ja laitesiirto on estetty.

## Tietolähteet

Verkkohakujen ja tietojen muunnosten lähtökohtana luettiin käyttäjän nykyistä projektia `aiempi-projekti` (Samsung-kansion handoff ohjaa sinne). Seuraavien palveluiden käyttö on toteutettu itsenäiseen uuteen sovellukseen; vanhaa projektia ei ole muutettu.

- [Ilmatieteen laitoksen avoin data](https://www.ilmatieteenlaitos.fi/avoin-data), WFS `fmi::forecast::edited::weather::scandinavia::point::simple` ja `fmi::observations::weather::simple`.
- [FMI:n sääsymbolit](https://en.ilmatieteenlaitos.fi/weather-symbols): SmartSymbol, erillinen yövariantti.
- [FMI:n aika- ja aurinkoparametrit](https://github.com/fmidev/smartmet-plugin-timeseries/blob/master/docs/Using-the-Timeseries-API.md) ja [auringon nousu- ja laskuajat](https://www.ilmatieteenlaitos.fi/aurinko-ja-kuu). WFS-haun `Sunrise`/`Sunset`-arvot varmennettiin oikealla verkkopyynnöllä. UTC-ajat kohdistetaan aurinkotapahtuman omaan Suomen päivämäärään, ei WFS-näytteen päivään.
- [Open-Meteo Forecast API](https://open-meteo.com/en/docs), henkilökohtainen käyttö, tunti- ja vuorokausiennusteet.
- [Eleringin rajapinta](https://dashboard.elering.ee/assets/swagger-ui/index.html), `nps/price`, Suomen `data.fi`.
- [Compose-teemat](https://developer.android.com/develop/ui/compose/designsystems/material3) ja [Android 17](https://developer.android.com/about/versions/17/setup-sdk).
- [Androidin adaptiiviset ja teemakuvakkeet](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive). Kuvake on oma vektoripiirros, ei ulkoinen kuva-aineisto.

Supersään neljä alkuperäistä kuvatiedostoa on säilytetty projektin juuressa suunnittelureferensseinä. Sovellus käyttää omia käyttöliittymäkomponentteja ja koodilla piirrettyjä symboleita.

Sadetutka, hintahistoria ja widgetit eivät sisälly tähän versioon.
