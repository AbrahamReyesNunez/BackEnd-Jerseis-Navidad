package com.jerseys.backend.admin;

import com.jerseys.backend.admin.AnalizadorSospechosos.DatosCuenta;
import com.jerseys.backend.admin.AnalizadorSospechosos.DatosVoto;
import com.jerseys.backend.admin.SospechososDtos.PanelSospechososResponse;
import com.jerseys.backend.usuario.UsuarioRepository;
import com.jerseys.backend.voto.VotoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Carga de la BD las cuentas y los votos válidos y se los pasa a AnalizadorSospechosos.
 * En un concurso de instituto son pocos cientos de filas: se analizan en memoria sin problema.
 */
@Service
public class PanelSospechososService {

    private final UsuarioRepository usuarios;
    private final VotoRepository votos;

    public PanelSospechososService(UsuarioRepository usuarios, VotoRepository votos) {
        this.usuarios = usuarios;
        this.votos = votos;
    }

    @Transactional(readOnly = true)
    public PanelSospechososResponse analizar() {
        List<DatosCuenta> cuentas = usuarios.findAll().stream()
                .map(u -> new DatosCuenta(u.getId(), u.getEmail(), u.getNombre(), u.getFechaRegistro(),
                        u.isVerificado(), u.isActivo(), u.getIpRegistroHash()))
                .toList();

        // Los votos ya anulados no cuentan: el admin ya se ocupó de ellos
        List<DatosVoto> votosValidos = votos.findAllParaAdmin().stream()
                .filter(v -> !v.isAnulado())
                .map(v -> new DatosVoto(v.getUsuario().getId(), v.getJersey().getId(), v.getPuntos(),
                        v.getFecha(), v.getIpHash()))
                .toList();

        return AnalizadorSospechosos.analizar(cuentas, votosValidos);
    }
}
