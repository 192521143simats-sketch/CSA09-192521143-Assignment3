# Programming in Java Assignment 3

**Student:** R Sarvesh  
**Register number:** 192521143  
**Java:** Oracle JDK 21.0.12.1

Open the [joint report (PDF)](Assignment%203%20Report.pdf) or the [Word report](Assignment%203%20Report.docx) for the answers, test tables and timing graphs. Each question folder contains source, console screenshots and the recorded run output.

Run the commands in each section from its question folder. Compile with Java 21 and keep the supplied text fixtures in place.

### Q1 ATM withdrawal

```powershell
javac -g Atm.java
'192521143 500' | java Atm
'abc 10' | java Atm card.txt uncaught
java Atm missing-card.txt
```

The valid withdrawal leaves 4,500. Numeric errors, invalid amounts, insufficient funds and missing cards take distinct paths, and every demonstrated path records closure.

### Q2 Examination submission

```powershell
javac -g StorageDemo.java ExamPortal.java
java ExamPortal
java StorageDemo
```

The portal reports domain-level rejections and preserves the original storage cause. StorageDemo matches the three expected lines.

### Q3 Daily sales report

```powershell
javac -g SalesReport.java
java SalesReport
jdb -classpath . SalesReport
```

The reports match. The malformed input leaves one open reader in the manual variant and zero in the try-with-resources variant. To reproduce the debugger trace, set `stop at SalesReport:57`, enter `run`, then `cont` three times to reach the malformed file. Use `locals`, `print SalesReport.openReaders.size()`, `step` and `where`.

### Q4 Answer script scanning

```powershell
javac -g Scanning.java
java Scanning
java Scanning daemon
```

Each of the 30 station trials counts 52 scripts. The logs show the completion order and the separate daemon experiment.

### Q5 Cinema seat booking

```powershell
javac -g SeatBooking.java SeatBookingUnsafe.java CinemaExperiment.java
1..10 | ForEach-Object { java SeatBookingUnsafe }
java SeatBooking
java CinemaExperiment
```

The synchronized completion sells 10 seats, and the forced race produces one duplicate in every trial. Both synchronized benchmark modes recorded zero duplicates.

### Q6 Wallet transfers and meal tokens

```powershell
javac -g TokenCounter.java TokenCounterIf.java Coordination.java
java TokenCounter
java Coordination
1..12 | ForEach-Object { java TokenCounterIf }
```

The completed counter returns 210. The alternate `if` implementation may produce an incorrect sum or stall. Stop a stalled trial with Ctrl+C. The coordination run detects the deliberate deadlock, completes both ordered transfers, and logs the counter sizes.

### Q7 Semester result processing

```powershell
javac -g ResultProcessor.java
java ResultProcessor
```

The program verifies the five supplied cases and generates its own temporary benchmark fixture. Each benchmark run reports 9,800 successes, 100 corrupt inputs and 100 missing inputs.

`evidence.txt` in each folder contains the raw output for the submitted run. The placeholder `<build>` in the transcripts refers to the temporary class output directory used during verification.

