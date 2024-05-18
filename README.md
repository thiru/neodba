# Neodba

**NOTE: This is alpha-quality software, full of rough edges and severely lacking in features.**

Neodba is a simple, depedency-free CLI tool for interacting with databases.

## Rationale

The main reason I wrote this app is to serve as the sole dependency of [this Neovim plugin](https://github.com/thiru/neodba.nvim). To meet this requirement the Neodba binary is a [single, static, native executable](https://www.graalvm.org/latest/reference-manual/native-image/guides/build-static-executables/). I.e. even though Neodba is written in Clojure it has **no runtime requirements**, including the JVM nor even libc.

## Installation

*NOTE: only Linux x64 architecture is supported for now.*

Simply grab the binary from the Releases page and extract it to a location in your `PATH`, e.g.:

```
tar -xf neodba-VERSION-x86_64-linux.tar.gz
sudo mv neodba /usr/bin
```

## Usage

### Config

Create a file named **db-spec.edn** to specify how to connect to your database. This file must be in the current working directory when running neodba. See [db-spec.edn](./db-spec.edn) for a Postgresql example. For complete documentation [look here](https://cljdoc.org/d/com.github.seancorfield/next.jdbc/CURRENT/api/next.jdbc#get-datasource).

### Sub-commands

#### e, eval

Evaluates the following arguments as a SQL statement.
In the example below the single quotes are used to avoid having to escape the asterisk as it's a special shell character:

```
neodba e 'select * from some_table'
```

There are a few helpers to retrieve database metadata such as the following:

```
neodba e '(get-catalogs)'
neodba e '(get-schemas)'
neodba e '(get-tables)'
neodba e '(get-views)'
```

#### r, repl

Starts a REPL where you can enter SQL statements.
It is recommended to run Neodba with rlwrap to get proper readline support (history, arrow keys, etc.):

```
rlwrap neodba r
```

## Database Support

**Postgresql** is currently the only supported database.

Since, at this stage, Neodba is essentially a POC, I didn't want to put the effort into supporting any additional databases. Though in theory it should be quite easy to add additional databases since the communication is done via JDBC. I.e. it should be as easy as including additional JDBC drivers.
