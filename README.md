# dime

Simple In-Memory database for currency conversion based on the [eurofxref](http://www.ecb.europa.eu/stats/eurofxref/eurofxref-hist.zip) data.

##Features:
- The database is updated automatically every day.
- If there is no rate for a currency on the given date (e.g holiday), the latest known is used.
- There are no rates for weekends so the friday rate is always used if present.

## Run tests

    $ lein tests

## Usage

    $ lein repl
    $ =>(require :reload '[dime.core :as dime])
    
    #Checks the rate for the given day
    $ => (dime/rate "2015-01-10" "SGD")
    $ {:currency "SGD", :rate 1.5789, :date "2015-01-09"}
    
    #Check if currency exists
    $ => (dime/currency-exists? "EUR")
    $ true
    
    #Convert currency
    $ => (dime/convert "2015-01-01" "EUR" "USD" 10)
    $ {:value 12.14, :currency "USD", :from-date "2015-01-01", :from-rate 1.0, :to-date "2014-12-31", :to-rate 1.2141}
        