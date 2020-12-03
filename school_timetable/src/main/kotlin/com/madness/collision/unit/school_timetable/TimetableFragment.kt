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

package com.madness.collision.unit.school_timetable

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import com.madness.collision.unit.school_timetable.data.Timetable
import com.madness.collision.unit.school_timetable.databinding.StTimetableBinding
import com.madness.collision.util.TaggedFragment

internal class TimetableFragment : TaggedFragment() {

    override val category: String = "ST"
    override val id: String = "Timetable"

    companion object {

        fun newInstance(): TimetableFragment {
            return TimetableFragment()
        }
    }

    private lateinit var mContext: Context
    private lateinit var mAdapter: TimetableAdapter
    private var timetableToLoad: Timetable? = null
    private lateinit var viewBinding: StTimetableBinding

    fun setTimetable(timetable: Timetable?) {
        mAdapter.timetable = timetable ?: Timetable()
        val lm = viewBinding.stTimetableRecycler.layoutManager as GridLayoutManager?
        if (timetable != null && lm?.spanCount != timetable.columns && timetable.columns > 0) {
            viewBinding.stTimetableRecycler.layoutManager = GridLayoutManager(context, timetable.columns)
        }
        mAdapter.notifyDataSetChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mContext = context ?: return
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewBinding = StTimetableBinding.inflate(inflater, container, false)
        return viewBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // initializing mAdapter in onCreate causes inflating error when rotating device
        mAdapter = TimetableAdapter(mContext, Timetable())
        viewBinding.stTimetableRecycler.adapter = mAdapter
        if (timetableToLoad != null) {
            setTimetable(timetableToLoad)
            timetableToLoad = null
        }
    }

}
