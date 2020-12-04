/*
 * Copyright 2020 Clifford Liu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.madness.collision.main

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.madness.collision.databinding.FragmentFrequentUnitsBinding
import com.madness.collision.settings.SettingsFunc
import com.madness.collision.unit.Description
import com.madness.collision.unit.Unit
import com.madness.collision.unit.UnitDescViewModel
import com.madness.collision.util.TaggedFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class FrequentUnitsFragment : TaggedFragment() {

    override val category: String = "FrequentUnits"
    override val id: String = "FrequentUnits"

    companion object {

        @JvmStatic
        fun newInstance() : FrequentUnitsFragment {
            return FrequentUnitsFragment()
        }
    }

    private lateinit var mViews: FragmentFrequentUnitsBinding
    private lateinit var mRecyclerView: RecyclerView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = context
        if (context != null) SettingsFunc.updateLanguage(context)
        mViews = FragmentFrequentUnitsBinding.inflate(inflater, container, false)
        return mViews.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val context: Context = context ?: return
        val mainViewModel: MainViewModel by activityViewModels()
        val descViewModel: UnitDescViewModel by activityViewModels()

        mRecyclerView = mViews.frequentUnitsRecyclerView

        val mAdapter = FrequentUnitsAdapter(context, object : FrequentUnitsAdapter.Listener {
            override val click: (Description) -> kotlin.Unit = {
                mainViewModel.displayUnit(it.unitName, shouldShowNavAfterBack = true)
                lifecycleScope.launch(Dispatchers.Default) {
                    Unit.increaseFrequency(context, it.unitName)
                }
            }
            override val longClick: (Description) -> Boolean = {
                it.descriptionPage?.apply {
                    mainViewModel.displayFragment(this, shouldShowNavAfterBack = true)
                }
                true
            }
        })
        mRecyclerView.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        mRecyclerView.adapter = mAdapter

        descViewModel.updated.observe(viewLifecycleOwner) {
            mAdapter.updateItem(it)
        }
    }
}
