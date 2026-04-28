package com.example.sportiva_booking_android.v2.activities;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.sportiva_booking_android.R;
import com.google.android.material.navigation.NavigationView;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout      drawerLayout;
    private NavigationView    navigationView;
    private Toolbar           toolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Esto hace que la app use toda la pantalla (quita el morado)
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        drawerLayout   = findViewById(R.id.main);
        navigationView = findViewById(R.id.nav_view);
        toolbar        = findViewById(R.id.toolbar);

        setupToolbar();
        setupNavigation();
        setupStatusBarPadding(); // Ajuste para que el icono no se tape
        showAllOptions();
    }

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

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            drawerLayout.openDrawer(GravityCompat.START);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupNavigation() {
        navigationView.setNavigationItemSelectedListener(item -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
    }

    private void showAllOptions() {
        Menu menu = navigationView.getMenu();
        menu.setGroupVisible(R.id.group_cliente, true);
        menu.setGroupVisible(R.id.group_pro,     true);
        menu.setGroupVisible(R.id.group_admin,   true);
        menu.setGroupVisible(R.id.group_root,    true);
    }
}