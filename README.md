# TODO List Manager

Manage a list of TODO items!

## Installation

Please download datomic-free-0.9.5350 from
[https://my.datomic.com/downloads/free](https://my.datomic.com/downloads/free)
and unzip it in the transactor directory so that relative to the project root, 
the files are in `transactor/datomic-free-0.9.5350`

If you have bash, curl and unzip, you can do this automatically.

    $ bin/download-transactor.sh

## Usage

First, start the Datomic transactor.

    $ bin/run-transactor.sh

Before running the application the first time, you should initialize the database.
This will add some example items the first time you run it.

    $ lein run -m todoapp.tasks.init-db

Then start the application.

    $ lein run

Then point your browser to the address you are serving at.
By default this is localhost port 3000.
[http://localhost:3000](http://localhost:3000)

## Options

You can optionally specify the path to a config edn file as the first argument
to the program. The default is in resources/default-config.edn 

## Examples

    $ lein run

    $ lein run resources/memory-config.edn

The configuration in resources/memory-config.edn will use an in-memory database.
It will not have any example list items.

## Upgrade notes

Earlier versions of the application did not support multiple TODO lists.
If you connect the application to a database set up by an earlier version,
all items will be migrated to the Default List.

Running a pre 0.2.0-SNAPSHOT version after upgrading the database may work,
but has not been extensively tested and is not officially supported.

## License

Copyright © 2016

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
