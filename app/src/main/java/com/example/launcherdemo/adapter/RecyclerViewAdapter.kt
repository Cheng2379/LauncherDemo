package com.example.launcherdemo.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.example.launcherdemo.BR
import com.example.launcherdemo.databinding.ItemAppBinding

/**
 *
 * @author cheng
 * @since 2025/4/1
 */
class RecyclerViewAdapter<T>(
    private var context: Context,
    var dataList: ArrayList<T>,
    private var layoutId: Int,
    private var callback: (viewHolder: ViewHolder, position: Int) -> Unit
) : RecyclerView.Adapter<ViewHolder>() {

    @SuppressLint("NotifyDataSetChanged")
    fun updateAll(newDataList: ArrayList<T>) {
        dataList.clear()
        dataList.addAll(newDataList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return MyViewHolder(
            DataBindingUtil.inflate(
                LayoutInflater.from(context),
                layoutId,
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return dataList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (holder is MyViewHolder) {
            holder.binding.setVariable(BR.appInfo, dataList[position])
        }
        callback(holder, position)
    }

    class MyViewHolder(val binding: ViewDataBinding) : ViewHolder(binding.root)
}