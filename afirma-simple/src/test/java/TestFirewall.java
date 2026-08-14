import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import es.gob.afirma.test.support.RequiresNetwork;

/** Prueba simple de conexi&oacute;n a <i>socket</i> local.
 * Para comprobar si el cortafuegos bloquea o no la conexi&oacute;n.
 * @author Tom&aacute;s Garc&iacute;a-Mer&aacute;s.*/
public final class TestFirewall {

	private static final int PORT = 6629;

	private static final String GREETING = "Conexion establecida"; //$NON-NLS-1$

	private static final Logger LOGGER = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$

	/** Comprueba que se puede abrir el puerto local de AutoFirma y conectarse a &eacute;l,
	 * es decir, que el cortafuegos no bloquea la conexi&oacute;n.
	 * @throws Exception En cualquier error no controlado. */
	@SuppressWarnings("static-method")
	@Test
	@Category(RequiresNetwork.class)
	public void testLocalSocketConnection() throws Exception {

		final CountDownLatch listening = new CountDownLatch(1);
		final AtomicReference<IOException> serverError = new AtomicReference<>();

		final Thread server = new Thread(
			() -> {
				try {
					createSocket(PORT, listening);
				}
				catch (final IOException e) {
					serverError.set(e);
					listening.countDown();
					LOGGER.log(Level.SEVERE, "Error creando el servicio servidor en el puerto " + PORT, e); //$NON-NLS-1$
				}
			}
		);
		server.setDaemon(true);
		server.start();

		Assert.assertTrue(
			"El servidor no se ha puesto a la escucha en el puerto " + PORT, //$NON-NLS-1$
			listening.await(10, TimeUnit.SECONDS)
		);
		Assert.assertNull(
			"No se ha podido abrir el puerto " + PORT + ": " + serverError.get(), //$NON-NLS-1$ //$NON-NLS-2$
			serverError.get()
		);

		final SocketFactory sf = SocketFactory.getDefault();
		try ( final Socket s = sf.createSocket(); ) {
			try {
				s.connect(
					new InetSocketAddress("127.0.0.1", PORT), //$NON-NLS-1$
					6000
				);
			}
			catch(final SocketTimeoutException | java.net.ConnectException ste) {
				Assert.fail("No se ha podido conectar al puerto " + PORT + ": " + ste); //$NON-NLS-1$ //$NON-NLS-2$
			}
			try ( final InputStream is = s.getInputStream(); ) {
				final byte[] d = getDataFromInputStream(is);
				Assert.assertEquals(
					"El servidor no ha devuelto el saludo esperado", //$NON-NLS-1$
					GREETING,
					new String(d)
				);
			}
		}
		finally {
			server.join(5000);
		}
	}

	static void createSocket(final int port, final CountDownLatch listening) throws IOException {
		final ServerSocketFactory ssf = ServerSocketFactory.getDefault();
		try (
			final ServerSocket ss = ssf.createServerSocket(port);
		) {
			ss.setSoTimeout(15000);
			listening.countDown();
			try (
				final Socket s = ss.accept();
				final OutputStream os = s.getOutputStream();
			) {
				os.write(GREETING.getBytes());
				os.flush();
			}
		}
	}

    /** Lee un flujo de datos de entrada y los recupera en forma de array de
     * bytes. Este m&eacute;todo consume pero no cierra el flujo de datos de
     * entrada.
     * @param input Flujo de donde se toman los datos.
     * @return Los datos obtenidos del flujo.
     * @throws IOException Cuando ocurre un problema durante la lectura. */
    public static byte[] getDataFromInputStream(final InputStream input) throws IOException {
        if (input == null) {
            return new byte[0];
        }
        int nBytes = 0;
        final byte[] buffer = new byte[32];
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while ((nBytes = input.read(buffer)) != -1) {
            baos.write(buffer, 0, nBytes);
        }
        return baos.toByteArray();
    }

}
