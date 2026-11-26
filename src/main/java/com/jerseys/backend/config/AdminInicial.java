package com.jerseys.backend.config;

import com.jerseys.backend.usuario.NormalizadorEmail;
import com.jerseys.backend.usuario.Rol;
import com.jerseys.backend.usuario.Usuario;
import com.jerseys.backend.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Se ejecuta una vez al arrancar la aplicación.
 * Si no existe ningún ADMIN y están definidas app.admin.email y app.admin.password,
 * crea el primer administrador (ya verificado). Los siguientes se nombran desde el panel.
 */
@Component
public class AdminInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInicial.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder codificador;
    private final AppProperties props;

    public AdminInicial(UsuarioRepository usuarios, PasswordEncoder codificador, AppProperties props) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.props = props;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        var admin = props.admin();
        if (usuarios.existsByRol(Rol.ADMIN)) {
            return;
        }
        if (admin == null || isBlank(admin.email()) || isBlank(admin.password())) {
            log.warn("No hay ningún ADMIN y no se han definido ADMIN_EMAIL / ADMIN_PASSWORD");
            return;
        }
        Usuario usuario = new Usuario(admin.email(), NormalizadorEmail.normalizar(admin.email()),
                admin.nombre() == null ? "Administrador" : admin.nombre(), codificador.encode(admin.password()));
        usuario.registrarConsentimiento(props.privacidad().versionActual(), Instant.now());
        usuario.setRol(Rol.ADMIN);
        usuario.setVerificado(true);
        usuarios.save(usuario);
        log.info("Creado el administrador inicial: {}", admin.email());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
