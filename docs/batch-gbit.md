# Batch Go Back in Time

Optional Curve 5.63.0 patch. Adds a Launchpad tile, native Curve selection controls, card selection, confirmation, foreground service and progress notification. Uses Curve's authenticated transaction services and its network-only eligibility query. The server-reported age limit filters the list; server eligibility remains authoritative.

Every charge is sequential. An atomic file in noBackupFilesDir records account, destination, approved quote, transaction IDs and outcomes before submitting a request. A failed/uncertain mutation pauses the queue. Refresh reconciles successful server moves; unresolved outcomes cannot be retried. Closing the screen does not stop the service. Stop prevents the next submission. Process death, logout, account changes and changed quotes require review. No boot restart or automatic retry.

The notification enters through Curve's normal authentication route. Reopen Batch GBIT from Launchpad to view detailed results. Merchant, amount and card details are omitted from notifications.

Completed results stay available until you choose **New batch**. Another signed-in account may replace a completed or archived journal without seeing its contents; unfinished or uncertain work still requires the owning account's review. Selecting purchases and cards preserves the existing controls, keyboard focus and carousel position. Progress updates retain result rows; completed results stop automatic polling and revalidate the account when reopened. Leaving the selection screen interrupts its eligibility scan and prevents further requests; an already-dispatched native request may still finish.

The service holds a partial wake lock while executing, with a 30-minute upper bound. It stops starting purchases when that bound expires and releases the lock on completion or shutdown. A request already submitted at expiry still requires outcome review. The native Curve date parser handles the REST API's UTC transaction timestamps. See [native regression checks](../tests/batch-gbit-native/README.md) and [offline queue checks](../tests/batch-gbit/README.md).

Never run financial tests against an account without explicit user authorization. Offline checks must cover partial success, changed quotes, interruption and duplicate starts. Visual and bridge checks use only the dedicated signed-out morphe_sample_api36 emulator. Build success alone does not prove live GBIT or background reliability.
