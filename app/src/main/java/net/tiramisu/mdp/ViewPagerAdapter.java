package net.tiramisu.mdp;

import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ViewPagerAdapter extends FragmentStateAdapter {

    private final SparseArray<Fragment> fragments = new SparseArray<>();

    public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        Fragment f;
        switch (position) {
            case 0: f = new HomeFragment(); break;
            case 1: f = new StatisticsFragment(); break;
            case 2: f = new TransactionsFragment(); break;
            case 3: f = new SettingsFragment(); break;
            default: f = new HomeFragment(); break;
        }
        fragments.put(position, f);
        return f;
    }

    @Override
    public int getItemCount() {
        return 4;
    }

    // Return fragment if created
    public Fragment getFragment(int position) {
        return fragments.get(position);
    }
}
