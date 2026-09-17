# 0.2.2:n kolmas katselmointi — 17.9.2026

Lähtötila: puhdas `main`, HEAD `dacdeb1`, edellinen koodikorjaus `6a761fa`. Raportti tarkistettiin nykyisestä tuotantokoodista, ja vahvistetut ongelmat sekä perustellut rakenneparannukset korjattiin commitissa **`9f9631a`**. Tämä dokumentti korvaa edellisen kierroksen nykytilaa koskevat väitteet; [edellinen raportti](REVIEW-0.2.2.md) säilyy historiana.

## Kohtakohtainen arvio

| # | Arvio ja toteutettu muutos |
|---|---|
| 1 | Vahvistettu. Paluu laitepaikkaan palauttaa sen viimeiset ennusteet muistista. Paikannuksen epäonnistuessa sää haetaan viimeisille laitekoordinaateille; verkkovirhe säilyttää vastaavan välimuistin. Ilman sijaintilupaa käyttäjän paluu tai päivitys voi myös hakea sään jo tunnetulle paikalle. Sijainnin virhe ja vanha paikannusaika säilyvät näkyvissä. |
| 2 | Vahvistettu. Ennusteet tallentuvat erikseen lähteen ja alkuperän mukaan (`FMI-DEVICE`, `OPEN_METEO-SELECTED` jne.). Suosikki ei korvaa laitepaikan ennustetta. Vanha nimetön origin hyväksytään laitepaikaksi vain, kun koordinaatit **ja paikannusaika** täsmäävät tallennettuun laitepaikkaan. Jo ylikirjoitettua ennustetta ei voida palauttaa eikä epäselvää suosikkia tunnisteta laitepaikaksi arvauksella. |
| 3 | Vahvistettu. Odotuksen päättyminen käynnistää tuoreen CAP-välimuistin arvioinnin prosessin IO-coroutinescopessa, myös virheessä tai ViewModelin sulkeutuessa. Se ei tarvitse elävää Compose-näkymää eikä aloita verkkohakua. Testi todentaa sekä uuden ilmoituksen että peruuntuneen ilmoituksen poistumisen ilman seuraavan workerin odottamista. |
| 4 | Vahvistettu. `reevaluate()` kertoo, löytyikö enintään 900 s vanha tilannekuva. Jos ei löydy, `WarningsViewModel.place()` yrittää syötettä uudelleen. Keskeneräisen verkkopyynnön tarkistus estää samanaikaiset uusinnat; tuore syöte käytetään edelleen uudelleen. |
| 5 | Vahvistettu. `NameAnchor` säilyttää viimeksi ratkaistun nimen koordinaatit ja tarkkuuden myös JSON-välimuistissa. Säilytyksen 50 m raja mitataan tästä muuttumattomasta ankkurista. Sekä ankkurin että uuden fixin tarkkuuden pitää olla enintään 200 m. Kaksi lyhyttä siirtymää ei enää siirrä nimeä yli rajan. |
| 6 | Vahvistettu. Onnistunut uudelleenarviointi poistaa kohdistusvirheen. Erillistä verkkohakuvirhettä ei samalla piiloteta; onnistunut verkkohaku poistaa sen. |
| 7 | Vahvistettu. Ennen klo 14:ää seuraava tarkistus on aina saman päivän klo 14, vaikka lupa puuttuu tai `afterAttempt` on tosi. Klo 14–16 tehdyn yrityksen jälkeen säilyy vähintään 15 minuutin väli. Kesä-/talviaika huomioidaan edelleen. |
| 8 | Omistajuusriski vahvistettu palvelutasolla; kuvattua kahden Activityn ajoitusta ei todennettu puhelimella. Odotus kuuluu nyt sen aloittaneelle palveluinstanssille. Vanhan instanssin `endLocationUpdate()` tai paikan tallennus ei voi vapauttaa tai korvata uuden aktiivisen paikannuksen tilaa. `onCleared()` säilyy omistajuustarkistuksen takana varmistuksena. |
| 9 | Vahvistettu. `warnings/place` on sekä ilmoitusten että kylmäkäynnistyksen laitepaikan yhteinen lähde. Repository ei enää kirjoita erillistä paikkakopiota. Vanha `weather-place-name` tuodaan vain, jos yhteinen paikka puuttuu; se ei korvaa olemassa olevaa varoituspaikkaa. |
| 10 | Perusteltu rakenteellinen vahvistus, ei osoitettua virheellistä tuotantokutsua. `Place`-konstruktorin oletus on nyt UNKNOWN. DeviceLocation merkitsee fixin nimenomaisesti DEVICEksi; haku tekee SELECTED-paikan. Tuntemattoman paikan tallentaminen varoituspaikaksi hylätään testissä. |
| 11 | Vahvistettu yksinkertaistus. Manifesti ei käytä erillistä worker-prosessia. Odotus ja sen omistaja ovat nyt prosessin muistissa `placeLock`-lukon takana. Prosessin kuollessa ne katoavat heti. Tallennettuja vanhoja `locationPending`/`pendingSince`-avaimia ei enää lueta, joten vanha lipputila ei estä ilmoituksia. TTL- ja kellonkorjauskoodi poistettiin. |
| 12 | Tarpeettomat synkroniset commit-kutsut vahvistettu, väitettyä 3–5 fsync-määrää ei. Android voi jättää muuttumattoman preferenssitilan kirjoittamatta. Aloitus/lopetus eivät nyt tee lainkaan preferenssikirjoituksia. Paikka tallennetaan vain sen sisällön muuttuessa. |
| 13 | Päällekkäisen hintahaun mahdollisuus vahvistettu; täsmälleen kolmea verkkopyyntöä ei mitattu. Etualan kytkentä siirtää workerin hakuikkunassa 15 minuutin päähän. `resume()` käyttää KEEP-käytäntöä, eikä `enable()` enää kutsu sitä toiseen kertaan. Jo ladattuja täydellisiä hintoja käytetään suoraan. Ilmoitusten tarkistukset sarjallistetaan, ja taustahaun aikaikkuna tarkistetaan lukon saamisen jälkeenkin. Sovelluksen tavallinen hintanäkymän päivitys säilyy erillisenä. |
| 14 | Vahvistettu. WarningFeed sisältää nyt myös asetukset, lupatarkistuksen ja ilmoitustestin. ViewModel käyttää vain injektoitua toteutusta, eikä luo toista WarningServiceä. Valetoteutuksella testataan myös käynnistys, kytkin, väli ja testipainike ilman oikeita preferenssi-/WorkManager-muutoksia. |
| 15 | Purun epäsymmetria vahvistettu; testijärjestyksestä riippuvaa kaatumista ei havaittu. Testi ottaa edellisen WorkManager-instanssin talteen, sulkee oman tietokantansa ja palauttaa delegaatin sisemmässä finally-lohkossa myös virheessä. Molemmat ajastintestit varmistavat palautuksen. |

## Varmennus ja paikallinen paketti

```powershell
$env:SAA_LOCATION_CHECK = '1'
$env:SAA_LIVE_API_TESTS = '1'
$env:SAA_PRICE_CHECK = '0'
.\gradlew.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease --console=plain
```

**104 testiä: 103 läpi, 0 epäonnistunutta, 1 ohitettu** (erillinen Nord Pool -vertailu). Molemmat APK-käännökset onnistuivat. Debug- ja release-lint: **0 virhettä, 4 aiempaa riippuvuuspäivitysvaroitusta** kummassakin. Oikeat MML-, FMI-, Open-Meteo- ja Elering-verkkotestit menivät läpi; hintavälimuistin vertailussa oli 192 varttia. `git diff --check` sekä UTF-8-tarkistus läpi.

Kohdennetut regressiot ovat `RepositoryRecoveryTest`, `LocationRecoveryTest`, `DevicePlaceNameTest`, `WarningRecoveryTest`, `WarningsTest`, `PriceAlertsTest` ja `PriceScheduleTest`. Ne kattavat muun muassa uuden/perutun CAP-varoituksen automaattisen käsittelyn odotuksen päätyttyä, kahden sijaintiomistajan kilpailun, vanhan välimuistin luokittelun sekä hintahaun odotuksen yli klo 16:n rajan.

Paikallinen allekirjoitettu `Saa-Sahko-0.2.2-release.apk`, SHA-256: **`A31F66F27A7A2D060EA2C9725B40F974AA39D8AE51816585D696996EF18D0B81`**. Allekirjoitus vastaa alkuperäistä release-varmennetta. Versio on 0.2.2 / versionCode 6. 0.2.0- ja 0.2.1-APK:t säilyivät muuttumattomina.

## Rajat ja lähteet

Puhelimissa ei tehty asennuksia tai kokeita, eikä akun kulutusta mitattu. Sovelluksen taustahaut ovat edelleen Androidin ajoituksen alaisia. Hintaverkkohaku aloitetaan taustalla vain Suomen klo 14–16 -ikkunassa; myöhästyneitä hakuja ei tehdä yöllä. Säävaroitukset seuraavat viimeksi saatua laitepaikkaa, ja matkalla uusi paikka vaatii sovelluksen avaamisen.

Aiemman kierroksen 10 sekunnin testiajastin, HTTP-siltojen yhtenäistäminen, testipainikkeiden rakenne ja MML-avaimen APK-upotus säilyivät ennallaan. GitHub-releasea ei tehdä tällä commit/push-toimituksella.

Kohta 12 tarkistettiin myös [AOSP:n SharedPreferencesImpl-toteutuksesta](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/app/SharedPreferencesImpl.java): muuttumaton muistigeneraatio voi ohittaa levykirjoituksen. Kohdan 15 purku tarkistettiin [AndroidX:n WorkManagerTestInitHelper-toteutuksesta](https://github.com/androidx/androidx/blob/androidx-main/work/work-testing/src/main/java/androidx/work/testing/WorkManagerTestInitHelper.java): tietokannan sulkeminen ei palauta delegaattia.
