
// Defines the package this class belongs to.
package com.example.signallingms1

// Android-specific imports for permissions, UI components, and activity lifecycle.
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/**
 * The main screen of the application after the user logs in.
 * This activity hosts a ViewPager2 with a TabLayout for navigating between the main
 * sections of the app: Profile, Shops, Cart, and Orders.
 * It also manages the navigation to a secondary fragment (ProductsFragment) when a user
 * selects a shop, and handles the back stack for this navigation.
 */
class HomeActivity : AppCompatActivity() {

    // --- UI and Navigation Variables ---
    private lateinit var viewPager: ViewPager2 // The swipable container for the main fragments.
    private lateinit var tabLayout: TabLayout // The tabs that control and display the ViewPager's current page.

    // --- Permission Handling ---
    // An ActivityResultLauncher to request the notification permission on Android 13 and higher.
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.d("HomeActivity", "Notification permission granted.")
        } else {
            android.util.Log.w("HomeActivity", "Notification permission was denied.")
        }
    }

    /**
     * Called when the activity is first created.
     * This is where the layout is set up, permissions are requested, and the ViewPager is configured.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the layout for this activity from the XML file.
        setContentView(R.layout.activity_home)

        // Create notification channels, which are required on Android 8.0 (API 26) and higher.
        NotificationHelper.createNotificationChannels(this)

        // Request permission to post notifications if on Android 13+ and permission isn't already granted.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Initialize the ViewPager and TabLayout.
        viewPager = findViewById(R.id.viewPager)
        tabLayout = findViewById(R.id.tabLayout)

        // Define the titles for the tabs.
        val tabTitles = listOf("Profile", "Shops", "Cart", "Orders")

        // Set the adapter for the ViewPager.
        viewPager.adapter = MainPagerAdapter(this)

        // Connect the TabLayout to the ViewPager.
        // This will automatically update the tabs when the user swipes, and vice-versa.
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()

        // Add a custom listener to handle back navigation from the ProductsFragment.
        setupBackStackListener()

        // Set up a custom callback to handle the hardware back button press.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Check if the ProductsFragment is currently visible.
                val fragmentContainer = findViewById<View>(R.id.fragmentContainer)
                if (fragmentContainer?.visibility == View.VISIBLE && supportFragmentManager.backStackEntryCount > 0) {
                    // If it is, pop the back stack to go back to the Shops list.
                    supportFragmentManager.popBackStack()
                } else {
                    // If not, perform the default back action (exit the app).
                    finish()
                }
            }
        })
    }

    /**
     * Sets up a listener to respond to changes in the fragment back stack.
     * This is used to hide the ProductsFragment container and show the main ViewPager
     * when the user navigates back from the product list.
     */
    private fun setupBackStackListener() {
        supportFragmentManager.addOnBackStackChangedListener {
            // If the back stack is empty, it means we have returned to the main tabs.
            if (supportFragmentManager.backStackEntryCount == 0) {
                // Make the main ViewPager visible again.
                findViewById<View>(R.id.fragmentContainer)?.visibility = View.GONE
                viewPager.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Navigates to the ProductsFragment to show the products for a selected seller.
     * This function hides the main ViewPager and shows a separate FragmentContainerView
     * that will host the ProductsFragment.
     *
     * @param seller The Seller object whose products are to be displayed.
     */
    fun navigateToProducts(seller: Seller) {
        // Create a new instance of ProductsFragment and pass the seller's ID and name as arguments.
        val productsFragment = ProductsFragment().apply {
            arguments = Bundle().apply {
                putString("sellerId", seller.uid)
                putString("sellerName", seller.shopName.ifEmpty { seller.name })
            }
        }

        // Get the container view for the fragment.
        val fragmentContainer = findViewById<View>(R.id.fragmentContainer)
        if (fragmentContainer != null) {
            // Hide the main ViewPager and show the fragment container.
            fragmentContainer.visibility = View.VISIBLE
            viewPager.visibility = View.GONE

            // Perform the fragment transaction to display the ProductsFragment.
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, productsFragment)
                .addToBackStack(null) // Add this transaction to the back stack.
                .commit()
        }
    }

    /**
     * An adapter for the ViewPager2 that provides the fragments for each tab.
     */
    private class MainPagerAdapter(
        fragmentActivity: FragmentActivity
    ) : FragmentStateAdapter(fragmentActivity) {

        // The total number of tabs/pages.
        override fun getItemCount(): Int = 4

        /**
         * Provides the appropriate fragment for a given position.
         */
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> ProfileFragment() // The first tab is the user's profile.
                1 -> ShopsFragment()   // The second tab shows the list of available shops.
                2 -> CartFragment()    // The third tab is the shopping cart.
                3 -> OrdersFragment()  // The fourth tab shows the user's past and current orders.
                else -> throw IllegalArgumentException("Invalid tab position: $position")
            }
        }
    }
}
