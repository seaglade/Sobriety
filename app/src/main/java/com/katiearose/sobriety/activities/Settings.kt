package com.katiearose.sobriety.activities

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.katiearose.sobriety.BuildConfig
import com.katiearose.sobriety.R
import com.katiearose.sobriety.shared.CacheHandler
import com.katiearose.sobriety.utils.applyThemes
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter

class Settings : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemes()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        companion object {
            private val sampleDate = LocalDate.of(2023, Month.JANUARY, 1)
        }

        private lateinit var cacheHandler: CacheHandler;

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)
            val themePref = requireNotNull(findPreference<ListPreference>("theme")) { "Wrong key passed for theme preference" }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                themePref.setEntries(R.array.theme_entries_p)
                themePref.setEntryValues(R.array.theme_entry_values_p)
            }
            cacheHandler = CacheHandler(requireContext())
            themePref.setOnPreferenceChangeListener { _, _ ->
                requireActivity().recreate()
                true
            }
            val materialYouPref = requireNotNull(findPreference<SwitchPreferenceCompat>("material_you")) { "Wrong key passed for M3 preference" }
            materialYouPref.setOnPreferenceChangeListener { _, _ ->
                requireActivity().recreate()
                true
            }
            val dateFormatPref = requireNotNull(findPreference<ListPreference>("date_format")) { "Wrong key passed for date format preference" }
            dateFormatPref.entries = dateFormatPref.entryValues.map {
                DateTimeFormatter.ofPattern(it.toString()).format(sampleDate)
            }.toTypedArray()

            val averageAttemptsWindow = requireNotNull(findPreference<EditTextPreference>("average_attempts_window"))
            averageAttemptsWindow.setOnBindEditTextListener { editText ->
                editText.inputType = InputType.TYPE_CLASS_NUMBER
            }

            val getExport = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
                if (uri != null) {
                    cacheHandler.exportData(Main.addictions, uri)
                }
            }
            findPreference<Preference>("data_export")?.setOnPreferenceClickListener {
                getExport.launch("sobriety_data.json")
                true
            }

            val getImport = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                if (uri != null) {
                    when (val imported = cacheHandler.importData(uri)) {
                        // Not a valid Sobriety backup (a toast was already shown)
                        null -> {}
                        else -> if (imported.isEmpty()) {
                            Toast.makeText(
                                requireContext(),
                                R.string.import_empty_file,
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            MaterialAlertDialogBuilder(requireContext())
                                .setTitle(R.string.import_title)
                                .setItems(
                                    arrayOf(
                                        getString(R.string.import_merge),
                                        getString(R.string.import_replace)
                                    )
                                ) { _, which ->
                                    // Keep a .bak copy before touching the saved data
                                    cacheHandler.backupCache()
                                    if (which == 0) {
                                        // Merge: skip habits whose name already exists (and duplicates within the file)
                                        val seen = Main.addictions.mapTo(HashSet()) { it.name }
                                        Main.addictions.addAll(imported.filter { seen.add(it.name) })
                                    } else {
                                        // Replace: only ever with a non-empty import
                                        Main.addictions.clear()
                                        Main.addictions.addAll(imported)
                                    }
                                    cacheHandler.writeCache(Main.addictions)
                                    val intent = Intent().putExtra("import", true)
                                    requireActivity().setResult(Activity.RESULT_OK, intent)
                                    requireActivity().finish()
                                }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                        }
                    }
                }
            }
            findPreference<Preference>("data_import")?.setOnPreferenceClickListener {
                getImport.launch(arrayOf<String>("application/json"))
                true
            }

            findPreference<Preference>("pref_app_version")?.summary = BuildConfig.VERSION_NAME

            findPreference<Preference>("pref_app_issue_tracker")?.setOnPreferenceClickListener {
                val i = Intent(Intent.ACTION_VIEW)
                i.data = Uri.parse(requireContext().getString(R.string.app_issue_url))
                startActivity(i)
                true
            }
        }
    }
}