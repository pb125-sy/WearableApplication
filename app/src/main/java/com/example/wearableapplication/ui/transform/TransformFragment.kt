package com.example.wearableapplication.ui.transform

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.wearableapplication.BluetoothBpmManager
import com.example.wearableapplication.R
import com.example.wearableapplication.databinding.FragmentTransformBinding
import com.example.wearableapplication.databinding.ItemTransformBinding

class TransformFragment : Fragment() {

    private var _binding: FragmentTransformBinding? = null
    private val binding get() = _binding!!

    private lateinit var transformViewModel: TransformViewModel
    private lateinit var bpmViewModel: BpmViewModel
    private var bluetoothManager: BluetoothBpmManager? = null

    // Permission launcher for Android 12+
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            startBluetooth()
        } else {
            binding.textBtStatus.text = "● Bluetooth permission denied"
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        transformViewModel = ViewModelProvider(this).get(TransformViewModel::class.java)
        bpmViewModel = ViewModelProvider(requireActivity()).get(BpmViewModel::class.java)

        _binding = FragmentTransformBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Setup existing RecyclerView
        val recyclerView = binding.recyclerviewTransform
        val adapter = TransformAdapter()
        recyclerView.adapter = adapter
        transformViewModel.texts.observe(viewLifecycleOwner) {
            adapter.submitList(it)
        }

        // Observe BPM LiveData
        bpmViewModel.bpm.observe(viewLifecycleOwner) { bpm ->
            binding.textBpm.text = bpm?.toString() ?: "--"
        }

        // Observe connection status
        bpmViewModel.status.observe(viewLifecycleOwner) { status ->
            binding.textBtStatus.text = status
        }

        // Connect button
        binding.btnConnectBt.setOnClickListener {
            checkPermissionsAndConnect()
        }

        return root
    }

    private fun checkPermissionsAndConnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ needs BLUETOOTH_CONNECT
            val needed = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (needed.isNotEmpty()) {
                permissionLauncher.launch(needed.toTypedArray())
            } else {
                startBluetooth()
            }
        } else {
            startBluetooth()
        }
    }

    private fun startBluetooth() {
        bluetoothManager?.disconnect()
        bluetoothManager = BluetoothBpmManager(
            onBpmReceived = { bpm ->
                bpmViewModel.updateBpm(bpm)
            },
            onStatusChanged = { status ->
                bpmViewModel.updateStatus(status)
            }
        )
        bluetoothManager?.connect()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bluetoothManager?.disconnect()
        _binding = null
    }

    // ---- Existing Adapter code unchanged below ----

    class TransformAdapter :
        ListAdapter<String, TransformViewHolder>(object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String) = oldItem == newItem
            override fun areContentsTheSame(oldItem: String, newItem: String) = oldItem == newItem
        }) {

        private val drawables = listOf(
            R.drawable.avatar_1, R.drawable.avatar_2, R.drawable.avatar_3,
            R.drawable.avatar_4, R.drawable.avatar_5, R.drawable.avatar_6,
            R.drawable.avatar_7, R.drawable.avatar_8, R.drawable.avatar_9,
            R.drawable.avatar_10, R.drawable.avatar_11, R.drawable.avatar_12,
            R.drawable.avatar_13, R.drawable.avatar_14, R.drawable.avatar_15,
            R.drawable.avatar_16,
        )

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransformViewHolder {
            val binding = ItemTransformBinding.inflate(LayoutInflater.from(parent.context))
            return TransformViewHolder(binding)
        }

        override fun onBindViewHolder(holder: TransformViewHolder, position: Int) {
            holder.textView.text = getItem(position)
            holder.imageView.setImageDrawable(
                ResourcesCompat.getDrawable(
                    holder.imageView.resources, drawables[position], null)
            )
        }
    }

    class TransformViewHolder(binding: ItemTransformBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val imageView: ImageView = binding.imageViewItemTransform
        val textView: TextView = binding.textViewItemTransform
    }
}