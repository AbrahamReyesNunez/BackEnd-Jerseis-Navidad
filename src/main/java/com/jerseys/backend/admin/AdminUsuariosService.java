package com.jerseys.backend.admin;

import com.jerseys.backend.admin.AdminDtos.UsuarioAdminResponse;
import com.jerseys.backend.comun.ApiException;
import com.jerseys.backend.usuario.Rol;
import com.jerseys.backend.usuario.Usuario;
import com.jerseys.backend.usuario.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminUsuariosService {

    private final UsuarioRepository usuarios;

    public AdminUsuariosService(UsuarioRepository usuarios) {
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public List<UsuarioAdminResponse> buscar(String texto) {
        List<Usuario> lista = texto == null || texto.isBlank()
                ? usuarios.findAllByOrderByFechaRegistroDesc()
                : usuarios.buscar(texto.trim());
        return lista.stream().map(UsuarioAdminResponse::de).toList();
    }

    @Transactional
    public UsuarioAdminResponse cambiarRol(Long adminId, Long usuarioId, Rol rol) {
        if (adminId.equals(usuarioId)) {
            // Evita que el último admin se quite el rol a sí mismo y nadie pueda administrar
            throw ApiException.peticionInvalida("NO_PUEDES_CAMBIARTE", "No puedes cambiar tu propio rol");
        }
        Usuario usuario = cargar(usuarioId);
        usuario.setRol(rol);
        return UsuarioAdminResponse.de(usuario);
    }

    @Transactional
    public UsuarioAdminResponse cambiarActivo(Long adminId, Long usuarioId, boolean activo) {
        if (adminId.equals(usuarioId)) {
            throw ApiException.peticionInvalida("NO_PUEDES_BLOQUEARTE", "No puedes bloquear tu propia cuenta");
        }
        Usuario usuario = cargar(usuarioId);
        usuario.setActivo(activo);
        return UsuarioAdminResponse.de(usuario);
    }

    private Usuario cargar(Long id) {
        return usuarios.findById(id)
                .orElseThrow(() -> ApiException.noEncontrado("USUARIO_NO_ENCONTRADO", "No existe ese usuario"));
    }
}
