# Neodba

**NOTE: This is alpha-quality software, full of rough edges and severely lacking in features.**

Neodba is a simple, depedency-free CLI tool for interacting with databases and serves as the base for this [Neovim plugin](https://github.com/thiru/neodba.nvim)

## Rationale

I wanted a Neovim database plugin that didn't require separate dependencies for each DBMS (e.g. psql for PostgreSQL, etc.). This CLI tools serves as this common base. It is written in Clojure and uses JDBC to communicate with the supported DBMS'. It is also a [single, static, native executable](https://www.graalvm.org/latest/reference-manual/native-image/guides/build-static-executables/) (with no system dependencies, not even libc).

## Installation

*NOTE: only Linux x64 architecture is currently supported.*

Simply grab the latest binary from [Releases](https://github.com/thiru/neodba/releases) and extract it to a location in your `PATH`, e.g.:

```shell
tar -xf neodba-VERSION-x86_64-linux.tar.gz
sudo mv neodba /usr/bin
```

## Usage

### Config

Create a file named **db-spec.edn** to specify how to connect to your database. This file must be in the current working directory when running neodba. See [db-spec.edn](./db-spec.edn) for a Postgresql example. For complete documentation see [this page](https://cljdoc.org/d/com.github.seancorfield/next.jdbc/CURRENT/api/next.jdbc#get-datasource).

### Sub-commands

#### e, eval

Evaluates the following arguments as a SQL statement.
In the example below single quotes are used to avoid having to escape the asterisk as it's a special character in most shells:

```shell
neodba e 'select * from some_table'
```

There are a few helpers to retrieve database metadata such as the following:

```shell
neodba e '(get-database-info)'
neodba e '(get-catalogs)'
neodba e '(get-schemas)'
neodba e '(get-tables)'
neodba e '(get-views)'
neodba e '(get-columns some_table)'
neodba e '(get-functions)'
neodba e '(get-procedures)'
```

#### f, file

Evaluate SQL in the given file, e.g.:

```shell
neodba f some-file.sql
```

#### r, repl

Starts a REPL where you can enter SQL statements.
It is recommended to run Neodba with rlwrap to get proper readline support (history, arrow keys, etc.):

```shell
rlwrap neodba r
```

## Database Support

**Postgresql** is currently the only supported database.

Since, at this stage, Neodba is essentially a POC, I didn't want to put the effort into supporting any additional databases. Though in theory it should be quite easy to add additional databases since the communication is done via JDBC. I.e. it should be as easy as including additional JDBC drivers.

## Development

### References

- [next-jdbc](https://github.com/seancorfield/next-jdbc) (a Clojure JDBC wrapper) is the primary interface to the database
- The [DatabaseMetaData](https://docs.oracle.com/en/java/javase/22/docs/api/java.sql/java/sql/DatabaseMetaData.html) interface is leveraged to provide database metadata such as available tables, table columns, available functions, etc.
