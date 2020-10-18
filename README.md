# BenchRestAPI Task

Solution to the Bench.io RestTest https://resttest.bench.co/ implemented in (reactive) Scala.

The solution goes a bit beyond minimal quick&dirty solution to make use of type safety and sketch possible extensions.

## Usage
To run the main application via sbt use:
```
sbt run
```

To run all tests via sbt use:
```
sbt test
```

## Scalability
The solution allows to fetch multiple pages in parallel (currently set to 4) to speed up processing of large result sets.
It scales indepdendently of the number of transactions processed.
Running balances are stored in-memory for each day, so memory-wise it scales by the number of days to compute the running balances over.
Although this limitation can also be resolved.

## Assumptions
* the REST api behaves mostly well-defined
* results returned within a single request are consistent
* tests cover some common edge cases / problems
* number of days to compute balances over is bounded
* failed requests are not retried
* failed pages are gracefully ignored
