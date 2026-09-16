# Suomen pörssisähkön hintavarmennus 16.9.2026

## Tulos

Eleringin Suomen aluehinnat ja Nord Poolin virallisen dataportaalin FI-hinnat vastasivat täsmälleen toisiaan **kaikissa 192 vartissa** päiville 16.–17.9.2026 Suomen ajassa. Puuttuvia tai eroavia jaksoja ei ollut. Vertailu tehtiin pyöristämättömille verottomille EUR/MWh-luvuille UTC-aikaleiman perusteella.

Sovelluksen `OfficialPricesLiveTest` varmisti lisäksi **480 hintanäyttöä**: 192 varttia ja 48 tuntikeskiarvoa, molemmat ALV päällä ja pois. Testi käytti sovelluksen omaa Repositorya, hintojen parseria, tuntikeskiarvoja ja kolmen desimaalin esitystä. Onnistunut ajo: **16.9.2026 klo 20.16.57 Suomen aikaa**.

Esimerkkejä 16.9.2026:

| Suomen aika | Nord Pool / Elering EUR/MWh | Veroton snt/kWh | ALV 25,5 % snt/kWh |
|---|---:|---:|---:|
| 00.00–00.15 | 5,30 | 0,530 | 0,665 |
| 12.00–12.15 | 6,75 | 0,675 | 0,847 |
| 20.00–20.15 | 26,89 | 2,689 | 3,375 |

Klo 20–21 tuntikeskiarvo ALV:n kanssa oli **2,992 snt/kWh**. Päivän hinnat ovat kaikki alle 5 snt/kWh myös ALV:n kanssa, joten uuden väriluokituksen mukaan ne kuuluvat vihreään luokkaan.

## Viralliset lähteet ja laskenta

- [Fingridin Tuntihinta-ohje](https://www.fingrid.fi/tuntihinta): Suomen aluehinta, varttihinnoittelu 1.10.2025 alkaen, tuntikeskiarvot ja valittava ALV-esitys.
- [Fingridin avoimen datan FAQ](https://data.fingrid.fi/en/faq): Fingrid ei omista pörssihintoja eikä voi jakaa niitä avoimessa rajapinnassaan. Fingrid ohjaa Nord Pooliin ja ENTSO-E Transparency Platformiin. Fingridin omia hintanumeroita ei siksi väitetä ristiintarkistetuiksi.
- [Nord Poolin virallinen FI-hintasivu](https://data.nordpoolgroup.com/auction/day-ahead/prices?deliveryDate=2026-09-16&currency=EUR&aggregation=DeliveryPeriod&deliveryAreas=FI). Vertailussa käytettiin sen julkisen dataportaalin `dataportal-api.nordpoolgroup.com/api/DayAheadPrices`-vastauksia, `market=DayAhead`, `deliveryArea=FI`, `currency=EUR`.
- [Eleringin hintasivu](https://dashboard.elering.ee/en/nps/price) ja sen `api/nps/price`-rajapinnan `data.fi`, joka on sovelluksen varsinainen hintalähde.
- [Verohallinnon verokannat](https://www.vero.fi/yritykset-ja-yhteisot/verot-ja-maksut/arvonlisaverotus/arvonlisaveroprosentit/): yleinen ALV 25,5 %.

EUR/MWh jaetaan kymmenellä, jotta saadaan snt/kWh. ALV lisätään kerran kertoimella 1,255. Tuntikeskiarvo lasketaan neljästä pyöristämättömästä vartista; näyttö pyöristetään kolmeen desimaaliin HALF_UP-säännöllä. Myyjän marginaali, sähkönsiirto ja sähkövero eivät kuulu näihin pörssihintoihin.

Nord Poolin toimituspäivä on Keski-Euroopan kalenteripäivä. Suomen kahden vuorokauden kattamiseen tarvittiin dataportaalista päivät 15., 16. ja 17.9.2026. Vertailussa käytettiin jaksojen todellisia UTC-aikoja, ei rivinumeroa tai samannimistä toimituspäivää.

## Ajantasaisuuden rajat ja toistaminen

Tämä tulos koskee tarkistushetkellä julkaistuja tietoja. Se ei takaa tulevien palveluvastausten virheettömyyttä. Sovellus hakee Eleringistä tämän päivän ja huomisen hinnat 15 minuutin välein etualalla sekä päivän vaihtuessa; nykyinen hintajakso vaihtuu näytössä kellon mukana. Viimeisin hakuaika näkyy ylhäällä. Epäonnistuneesta hausta näytetään virheilmoitus, puuttuvaa varttia ei täytetä toisella hinnalla eikä vajaalle tunnille lasketa keskiarvoa.

Nord Pool -vertailu ei ole jatkuva taustapalvelu eikä sovelluksen ajonaikainen riippuvuus. Sen voi ajaa uudelleen README:n `SAA_PRICE_CHECK=1`-komennolla. Testi voi epäonnistua myös verkkokatkoon, julkaisun kesken olevaan päivitykseen tai virallisen dataportaalin rajapintamuutokseen; tulosta ei silloin pidä tulkita onnistuneeksi varmennukseksi.

Paikalliset raakavastaukset ja ensimmäisen vertailun kooste: `app/build/price-verification`. Varsinaisen sovelluslaskennan vertailun raportti: `app/build/test-results/testDebugUnitTest/TEST-fi.omasaasahko.OfficialPricesLiveTest.xml`. Build-hakemistojen aineistot voivat poistua siivouksessa.


## Huomisen hintailmoitus (0.2.0)

Arkikeskuksen mobiilitoteutus luettiin tiedostosta `aiempi-projekti`. Se käyttää Eleringiä, opt-in-asetusta ja toimituspäivään sidottua toistojen estoa. Vanhaa projektia ei muutettu. Uudessa sovelluksessa vaaditaan kaikkien Suomen paikallisen vuorokauden varttien saatavuus, ei pelkästään iltatunnin läsnäoloa. Puuttuva tai ristiriitainen vartti estää ilmoituksen. Kellonsiirtopäivinä odotetaan 92/100 varttia.

Nord Poolin [markkinakuvaus](https://www.nordpoolgroup.com/en/the-power-market/Day-ahead-market/) käsittelee seuraavan päivän huutokauppaa. [Virallinen viivästystiedote 14.9.2025](https://www.nordpoolgroup.com/en/trading/Operational-Message-List/2025/09/day-ahead-new-publication-time---market-coupling-results-expected-at-1250-cet--20250914104900/) osoittaa, että julkaisuaika voi muuttua. Siksi ilmoitusta ei laukaista kellon perusteella. Sen tiedot ovat samat Elering/Nord Pool FI -hinnat kuin sähkösivulla, laskenta BigDecimalilla ja näyttö 0,000.

Ilmoitus sisältää keskihinnan ja halvimman/kalleimman tuntikeskiarvon, voimassa olevan ALV-valinnan mukaisesti. Luvan puuttuminen ei kuluta päivän ilmoitusta. Toistojen esto säilyy prosessin vaihtuessa. Ilmoituksen päivämäärä välitetään avaamiseen: seuraavana päivänä avattu ilmoitus näyttää tämän päivän, ei uutta huomista.

`PriceAlertsTest` kattaa puutteelliset/risteävät vartit, ALV:n, 23/24/25 tunnin päivät, luvan ja toistojen eston. `ScreenTest` kattaa erillisen kytkimen ja ilmoituksen päiväkohdistuksen. Taustahaku on 30 minuutin WorkManager-työ, ei välitön FCM-push; radio-/Doze-viivettä tai akkukulutusta ei ole mitattu.
