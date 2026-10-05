# Sään ja varoitusten varmennus 16.9.2026

## Sää

FMI:n [virallinen symbolitaulukko](https://en.ilmatieteenlaitos.fi/weather-symbols) ja [Open-Meteon dokumentaatio](https://open-meteo.com/en/docs) luettiin ja verrattiin sovelluksen koodeihin. FMI:n 45 päivämerkkiä ja niiden 45 yövarianttia sekä Open-Meteon 28 WMO-koodia on katettu testeillä.

Korjaukset: FMI 61/64/67 tarkoittaa rakeita, ei räntää. Jäätävä tihku ja sade erotetaan tavallisesta sateesta (FMI 14/17, WMO 56/57/66/67); WMO 96/99 erottaa ukkosen ja rakeet. Melkein selkeä ja melkein pilvinen saivat omat luokat. Voimakkuus ja kuurojen luonne säilytetään tekstikuvauksessa. Tuntematon FMI-koodi 201 ei enää muutu selkeäksi modulo-operaatiolla.

FMI:n ennuste tulee kyselystä `fmi::forecast::edited::weather::scandinavia::point::simple`, `latlon=lat,lon`, `timestep=60`. Parametrit: Temperature, FeelsLike, WindSpeedMS, WindDirection, Precipitation1h, SmartSymbol, Sunrise, Sunset. Palvelun DescribeStoredQueries sekä observableProperty-metatiedot tarkistettiin. Havaintojen bbox ja lähimmän aseman erillinen käsittely säilyvät.

Open-Meteolta pyydetään nimenomaisesti Celsius, m/s, mm, UTC-epoch-aika ja `timezone=auto`. Vastauksen aikavyöhyke määrittää paikalliset päivät. Tuntisade kuvaa edellistä tuntia; FMI:n vuorokausisumma korjattiin käyttämään päivän klo 01:n ja seuraavan keskiyön välisiä näytteitä. Viimeinen keskiyö haetaan mukaan seitsemännen päivän sadesummaan. Kesäaikapäivien 23/25 tuntia ja puuttuva loppunäyte testataan. FMI:n [sääpalveluiden ohje](https://www.ilmatieteenlaitos.fi/saapalvelut-verkossa) vahvistaa sadekertymän aikarajan.

FMI:n päiväkuvake on lähimpänä keskipäivää oleva symboli. Open-Meteon päiväkuvake on palvelun määrittelemä päivän merkittävin sää. Tämä ero kerrotaan käyttöliittymässä. Mallien lämpötilojen, sademäärien, ruutupisteiden ja aurinkoaikojen keskinäistä yhtäsuuruutta ei oleteta.

`WeatherProviderLiveTest` hakee molemmat ennusteet sovelluksen Repositorylla julkiselle vertailupisteelle Vantaalla ja vertaa näytettäviä lukuja alkuperäiseen JSONiin ja erillisellä DOM-parserilla luettuun WFS-XML:ään. 16.9.2026 klo 20.49 Suomen aikaa onnistui: FMI 169 tuntia / 7 päivää, Open-Meteo 168 tuntia / 7 päivää. Yksiköt, aikaleimat, aurinkoajat, min/max, sade, tuuli ja yömerkinnät täsmäsivät. Välimuistin palautuminen tarkistetaan lisäksi RepositoryLiveTestissä.

## Varoitukset

Lähtökohdaksi luettiin aiemman oman Arkikeskus-projektin `WarningsClient` ja `WarningsRepository`. Niitä ei muutettu. Arkikeskus käyttää MeteoAlarm-välityspalvelua, suodattaa seuraavaan 24 tuntiin eikä kyseisessä haussa kohdista koordinaatteja polygonien avulla.

Uusi toteutus käyttää suoraan [FMI:n suomenkielistä fat Atom -syötettä](https://alerts.fmi.fi/cap/feed/atom_fi-FI.xml). Sopimus tarkistettiin [FMI:n pikaohjeesta](https://www.ilmatieteenlaitos.fi/varoitusten-latauspalvelun-pikaohje) ja [FMI CAP -profiilista 1.1.0](https://alerts.fmi.fi/cap/profile/current/):

- Vain Actual/Public-varoitukset, ensisijaisesti fi-FI. Moderate = keltainen, Severe = oranssi, Extreme = punainen.
- Alert/Update/Cancel ja references huomioidaan. Onnistunut täysi syöte korvaa aiemman; poistuneita varoituksia ei jätetä voimaan.
- Alue tarkistetaan CAP-polygonista, jonka järjestys on **leveysaste,pituusaste**. Pelkkä alueen nimi ei laajenna varoitusta maakuntaan. Vantaan Tikkurila osuu FMI:n Uudenmaan polygonin sisään, Tampere ei.
- Kaikki julkaistut tulevat varoitukset säilytetään, mukaan lukien Future-kiireellisyys. Päivät esitetään Suomen ajassa, loppuaika on poissulkeva.
- Testi-/harjoitusviestejä ja peruttuja tai päättyneitä varoituksia ei ilmoiteta. Uudelleenjulkaistu samansisältöinen viesti ei ilmoita uudelleen. Tason/ajan/kuvauksen muutos voi ilmoittaa.
- Tuntematon taso tai puuttuva aluegeometria merkitsee aineiston osittaiseksi. Virheellinen syöte ei muutu onnistuneeksi tyhjäksi listaksi. Vanhempaa julkaisua ei hyväksytä uuden päälle.

`WarningsTest` kattaa nämä rajat, monikulmion loven ja reunapisteen sekä ilmoitusten poistamisen ja toistojen eston. `fmi-uusimaa-polygon.txt` on FMI:n [1.7.2026 klo 12.01Z arkistosyötteen](https://alerts.fmi.fi/cap/feed/2026/07-01/12-01-00Z-atom_fi-FI.xml) Uusimaa-alue. Aineisto: Ilmatieteen laitos, CC BY 4.0, [käyttöehdot](https://www.ilmatieteenlaitos.fi/avoin-data-lisenssi).

Oikea CAP-verkkohaku onnistui 16.9.2026; ajankohdan 20.49 tarkistuksessa oli kuusi voimassa olevaa/tulevaa varoitusta, joista yksi kohdistui vertailusijaintiin. Tämä on ajankohtainen tarkistustulos, ei pysyvä varoitustilanne.

## Ilmoitusten käytännön rajat

Ilmoitukset otetaan käyttöön Varoitukset-sivun kytkimellä ja Androidin luvalla. Kaikki kolme tasoa sisältyvät. Tarkistusväli 15/30/60 min, oletus 30 min. [Android WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work) voi viivästyttää työtä Dozessa, virransäästössä tai verkon puuttuessa. Tämä on puhelimen taustahaku ja paikallinen ilmoitus, ei palvelimelta välittömästi lähetettävä FCM-push.

Taustalla ei paikanneta: käytetään sovelluksessa viimeksi haettua paikkaa. Matkalla sovellus täytyy avata, jotta seurattava paikka vaihtuu. Likimääräinen sijainti voi vaikuttaa kohdistukseen alueen rajalla. Seurattava paikka ja sijainnin hakuaika näkyvät sivulla. Pitkän aikavälin akkukulutusta ja todellisen varoituksen saapumisviivettä ei ole mitattu.

## Toistaminen

```powershell
$env:SAA_LIVE_API_TESTS = '1'
$env:SAA_PRICE_CHECK = '1'
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease --console=plain
Remove-Item Env:SAA_LIVE_API_TESTS,Env:SAA_PRICE_CHECK
```

Julkaisu-APK:n allekirjoitus edellyttää paikallista release-avainta. Tavallisessa testiajossa verkkotestit ohitetaan. Yksittäinen onnistunut varmennus ei takaa ulkoisten palveluiden tulevien vastausten virheettömyyttä tai ennusteen toteutumista.


Varoitussivun päivärivillä on Kaikki sekä viisi vuorokautta eteenpäin nykyinen päivä mukaan lukien. Myös varoitukseton päivä on valittavissa ja kertoo, ettei sille ole julkaistu alueen varoituksia. FMI:n [varoitusohje](https://www.ilmatieteenlaitos.fi/tietoa-varoituksista) vahvistaa viiden vuorokauden käytännön. Lauantain 19.9.2026 Uudenmaan keltainen tuulivaroitus tarkistettiin suoraan syötteestä: voimassa 13–18 Suomen aikaa, kuvaus mainitsee 20 prosentin todennäköisyyden ja 15 m/s puuskat.

Lisäksi luettiin Arkikeskuksen uudemman mobiiliversion `WeatherWarningNotifier`: se kohdistaa kotikunnan/maakunnan nimien kautta. Tässä sovelluksessa käytetään varsinaista CAP-polygonia, jotta maakuntaa pienempi vapaa aluerajaus kohdistuu oikein.
