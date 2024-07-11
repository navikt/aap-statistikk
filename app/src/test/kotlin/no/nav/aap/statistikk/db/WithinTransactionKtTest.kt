package no.nav.aap.statistikk.db

import io.mockk.*
import no.nav.aap.statistikk.api.postgresDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.Connection
import java.sql.SQLException
import javax.sql.DataSource

class WithinTransactionKtTest {
    @Test
    fun `om feilmelding under commit, så får vi TransaksjonsAvbrudd`() {
        val mockk = mockk<DataSource>()
        val mockConnection = mockk<Connection>()

        every { mockk.connection } returns mockConnection
        every { mockConnection.close() } returns Unit
        every { mockConnection.autoCommit = any() } answers { }
        every { mockConnection.rollback() } just Runs

        every { mockConnection.commit() } throws SQLException("feilmelding fra SQL")

        val avbrudd = assertThrows<TransaksjonsAvbrudd> {
            mockk.withinTransaction { 1 }
        }
        assertThat(avbrudd.message).contains("feilmelding fra SQL")
    }

    @Test
    fun `om dataSource ikke klarer å koble til, så får vi TilkoblingsAvbrud`() {
        val dataSource = spyk(postgresDataSource())

        every { dataSource.connection } throws SQLException("feilmelding fra SQL")

        assertThrows<TilkoblingsAvbrudd> {
            dataSource.withinTransaction { ds -> ds }
        }
    }
}