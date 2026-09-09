package mk.wf.labelscanner

data class PackageRecord(
    val id: Long = 0,
    val createdAt: String,
    val nalog: String,
    val packageNo: String,
    val article: String,
    val size: String,
    val quantity: Int,
    val customer: String,
    val barcode: String,
    val rawText: String,
    val photoPath: String
)
