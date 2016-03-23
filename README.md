# TODO List Manager

Manage a list of TODO items!

## Installation

Please download datomic-free-0.9.5350 from
[https://my.datomic.com/downloads/free](https://my.datomic.com/downloads/free)
and unzip it in the transactor directory so that relative to the project root, 
the files are in `transactor/datomic-free-0.9.5350`

If you have bash, curl and unzip, you can do this automatically.

    $ bin/download-transactor.sh

Before running the application, you should also initialize the database.

    $ lein run -m todoapp.tasks.init-db

## Usage

First, start the Datomic transactor.

    $ bin/run-transactor.sh

Then start the application.

    $ lein run [config]

Then point your browser to the address you are serving at.
By default this is localhost port 3000.
[http://localhost:3000](http://localhost:3000)

## Options

You can optionally specify the path to a config edn file as the first argument
to the program. The default is in resources/default-config.edn 

## Examples

    $ lein run

    $ lein run resources/default-config.edn


## License

Copyright © 2016

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
