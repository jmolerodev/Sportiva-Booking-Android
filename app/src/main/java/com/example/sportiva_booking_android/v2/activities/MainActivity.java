package com.example.sportiva_booking_android.v2.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.sportiva_booking_android.R;
import com.example.sportiva_booking_android.v2.enums.Rol;
import com.example.sportiva_booking_android.v2.fragments.AdminListFragment;
import com.example.sportiva_booking_android.v2.fragments.HomeFragment;
import com.example.sportiva_booking_android.v2.fragments.ProfileFragment;
import com.example.sportiva_booking_android.v2.fragments.UserManagementFragment;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    /*Claves para SharedPreferences*/
    private static final String PREFS_NAME   = "SportivaPrefs";
    private static final String KEY_REMEMBER = "rememberUser";

    /*Componentes de la vista*/
    private DrawerLayout   drawerLayout;
    private NavigationView navigationView;
    private Toolbar        toolbar;

    /*Rol del usuario que recibimos desde LoginActivity*/
    private Rol userRol;

    /*Instancia de Firebase Authentication*/
    private FirebaseAuth firebaseAuth;

    /*SharedPreferences para limpiar la preferencia de "Recordar usuario" al cerrar sesión*/
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*Esto hace que la app use toda la pantalla*/
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        /*Inicializamos Firebase Auth*/
        firebaseAuth = FirebaseAuth.getInstance();

        /*Inicializamos SharedPreferences*/
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        /*Inicializamos los componentes de la vista*/
        drawerLayout   = findViewById(R.id.main);
        navigationView = findViewById(R.id.nav_view);
        toolbar        = findViewById(R.id.toolbar);

        /*Recuperamos el rol enviado desde LoginActivity*/
        recuperarRol();

        /*Configuramos la Toolbar como ActionBar*/
        setupToolbar();

        /*Configuramos el listener del menú lateral*/
        setupNavigation();

        /*Ajustamos el padding para que la Toolbar no quede tapada por la cámara/reloj*/
        setupStatusBarPadding();

        /*Mostramos únicamente las opciones del menú que corresponden al rol del usuario*/
        setupMenuPorRol();

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, HomeFragment.newInstance(userRol))
                    .commit();
        }
    }

    /**
     * Recupera el rol del usuario desde el Intent y lo convierte al enum Rol.
     * Si el extra no existe o no coincide con ningún valor conocido, se asigna CLIENTE por defecto.
     */
    private void recuperarRol() {
        String rolString = getIntent().getStringExtra("ROL");

        if (rolString != null) {
            try {
                userRol = Rol.valueOf(rolString);
            } catch (IllegalArgumentException e) {
                /*Si el valor no coincide con ningún enum, asignamos CLIENTE por defecto*/
                userRol = Rol.CLIENTE;
            }
        } else {
            /*Si no se recibió el extra, asignamos CLIENTE por defecto*/
            userRol = Rol.CLIENTE;
        }
    }

    /**
     * Configura la visibilidad de los grupos del menú lateral según el rol del usuario.
     * Cada rol ve sus propias opciones, el botón de inicio y el de cerrar sesión.
     */
    private void setupMenuPorRol() {
        Menu menu = navigationView.getMenu();

        /*Ocultamos todos los grupos primero*/
        menu.setGroupVisible(R.id.group_home,    false);
        menu.setGroupVisible(R.id.group_cliente, false);
        menu.setGroupVisible(R.id.group_pro,     false);
        menu.setGroupVisible(R.id.group_admin,   false);
        menu.setGroupVisible(R.id.group_root,    false);

        /*Mostramos únicamente el grupo que corresponde al rol del usuario*/
        switch (userRol) {
            case CLIENTE:
                menu.setGroupVisible(R.id.group_cliente, true);
                break;
            case PROFESIONAL:
                menu.setGroupVisible(R.id.group_pro, true);
                break;
            case ADMINISTRADOR:
                menu.setGroupVisible(R.id.group_admin, true);
                break;
            case ROOT:
                menu.setGroupVisible(R.id.group_root, true);
                break;
        }

        /*Opciones comunes para todos los roles*/
        menu.setGroupVisible(R.id.group_home,   true);
        menu.setGroupVisible(R.id.group_logout, true);
    }

    /**
     * Configura la Toolbar como ActionBar y establece el icono de menú lateral.
     */
    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.lateral_menu);
        }
    }

    /**
     * Ajusta la Toolbar para que baje un poco y no la tape la cámara/reloj,
     * pero dejando que el fondo verde suba hasta arriba.
     */
    private void setupStatusBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, systemBars.top, 0, 0);
            return insets;
        });
    }

    /**
     * Gestiona los clicks sobre los items de la Toolbar.
     * Al pulsar el icono de hamburguesa, abre el DrawerLayout.
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            drawerLayout.openDrawer(GravityCompat.START);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Configura el listener del NavigationView para gestionar
     * la navegación entre las opciones del menú lateral.
     */
    private void setupNavigation() {
        navigationView.setNavigationItemSelectedListener(item -> {

            /*Cerramos el drawer al seleccionar cualquier opción*/
            drawerLayout.closeDrawer(GravityCompat.START);

            /*Opción común: Volver al Inicio (limpia el backstack)*/
            if (item.getItemId() == R.id.nav_home) {
                getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                return true;
            }

            if (item.getItemId() == R.id.nav_logout) {
                cerrarSesion();
                return true;
            }

            if (item.getItemId() == R.id.nav_perfil){
                navegarAFragment(ProfileFragment.newInstance(userRol));
                return true;
            }

            /*ROOT: alta de administradores*/
            if (item.getItemId() == R.id.nav_root_1) {
                navegarAFragment(UserManagementFragment.newInstance(userRol));
                return true;
            }

            /*ROOT: lista de administradores*/
            if (item.getItemId() == R.id.nav_root_2){
                navegarAFragment(AdminListFragment.newInstance(userRol));
                return true;
            }

            /*ADMIN: alta de profesionales — mismo Fragment, distinto rol*/
            if (item.getItemId() == R.id.nav_adm_1) {
                navegarAFragment(UserManagementFragment.newInstance(userRol));
                return true;
            }

            return true;
        });
    }

    /**
     * Sustituye el fragmento actual por el indicado y lo añade a la back stack
     * para que el botón Atrás devuelva al HomeFragment automáticamente.
     *
     * @param fragment Fragment destino ya instanciado con sus argumentos
     */
    private void navegarAFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .addToBackStack(null)
                .commit();
    }

    /**
     * Método utilitario para mostrar Snackbars centrados.
     */
    private void showCenteredSnackbar(String message) {
        Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content),
                message,
                Snackbar.LENGTH_LONG);

        /*Centramos el texto del Snackbar*/
        TextView textView = snackbar.getView().findViewById(
                com.google.android.material.R.id.snackbar_text
        );

        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);

        snackbar.show();
    }

    /**
     * Cierra la sesión del usuario en Firebase Auth, limpia la preferencia
     * de "Recordar usuario" en SharedPreferences y navega de vuelta al Login.
     */
    private void cerrarSesion() {
        firebaseAuth.signOut();

        sharedPreferences.edit()
                .putBoolean(KEY_REMEMBER, false)
                .apply();

        showCenteredSnackbar("Has cerrado la sesión con éxito. ¡Vuelve pronto!");

        /*Esperamos a que el Snackbar sea visible antes de navegar*/
        findViewById(android.R.id.content).postDelayed(() -> {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }, 1500);
    }
}