package net.tiramisu.mdp;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SettingsFragment extends Fragment {
    public SettingsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvUserEmail = view.findViewById(R.id.tvUserEmail);
        Button btnLogout = view.findViewById(R.id.btnLogout);

        // Show current user email if available
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && tvUserEmail != null) {
                String email = user.getEmail();
                tvUserEmail.setText(email != null ? email : getString(R.string.welcome));
            }
        } catch (Exception ignored) {}

        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                try {
                    FirebaseAuth.getInstance().signOut();
                } catch (Exception ex) {
                    // ignore signout exceptions
                }
                Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show();
                Intent i = new Intent(requireContext(), LoginActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(i);
                if (getActivity() != null) getActivity().finish();
            });
        }
    }
}
