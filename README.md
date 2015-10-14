# Google news analyzer

Google news feed analyzer micro service.
Written in Scala, using Akka Http and Akka Streams.

## Usage

Start server with sbt:

    $ sbt
    > ~re-start

and make POST request to it:


    $ curl -X POST -H 'Content-Type: application/json' http://localhost:9000/analyzenews -d '{"news": [{"query": "scala akka", "number": 20}, {"query": "scala best practices", "number": 10}]}'
    {
      "date": "10 October 2015 18:55:02",
      "result": {
        "adtmag.com": 1,
        "readwrite.com": 3,
        "marketwired.com": 1,
        "infoq.com": 5,
        "itbusinessedge.com": 1,
        "forbes.com": 1,
        "ambiente.regione.emilia-romagna.it": 1,
        "technical.ly": 1,
        "gigaom.com": 2,
        "enterpriseappstoday.com": 1,
        "zdnet.com": 1,
        "geekwire.com": 1,
        "ew.com": 1,
        "jaxenter.de": 1,
        "techrepublic.com": 2,
        "sys-con.com": 3
      }
    }



