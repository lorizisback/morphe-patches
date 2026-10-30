# Batch Go Back in Time

Optional Curve 5.63.0 patch. Adds a Launchpad tile, native Curve selection controls, card selection, confirmation, foreground service and progress notification. Uses Curve's authenticated transaction services and its network-only eligibility query. The server-reported age limit filters the list; server eligibility remains authoritative.

Every charge is sequential. An atomic file in noBackupFilesDir records account, destination, approved quote, transaction IDs and outcomes before submitting a request. A failed/uncertain mutation pauses the queue. Refresh reconciles successful server moves; unresolved outcomes cannot be retried. Closing the screen does not stop the service. Stop prevents the next submission. Process death, logout, account changes and changed quotes require review. No boot restart or automatic retry.

The notification enters through Curve's normal authentication route. Reopen Batch GBIT from Launchpad to view detailed results. Merchant, amount and card details are omitted from notifications.

Never run financial tests against an account without explicit user authorization. Offline checks must cover partial success, changed quotes, interruption and duplicate starts. Visual and bridge checks use only the dedicated signed-out morphe_sample_api36 emulator. Build success alone does not prove live GBIT or background reliability.
