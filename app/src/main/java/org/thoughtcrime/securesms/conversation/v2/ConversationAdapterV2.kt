/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.conversation.v2

import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.google.android.exoplayer2.MediaItem
import org.signal.core.util.logging.Log
import org.signal.core.util.toOptional
import org.thoughtcrime.securesms.BindableConversationItem
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.conversation.ConversationAdapter.ItemClickListener
import org.thoughtcrime.securesms.conversation.ConversationAdapterBridge
import org.thoughtcrime.securesms.conversation.ConversationHeaderView
import org.thoughtcrime.securesms.conversation.ConversationItemDisplayMode
import org.thoughtcrime.securesms.conversation.ConversationMessage
import org.thoughtcrime.securesms.conversation.colors.Colorizable
import org.thoughtcrime.securesms.conversation.colors.Colorizer
import org.thoughtcrime.securesms.conversation.mutiselect.MultiselectPart
import org.thoughtcrime.securesms.conversation.mutiselect.Multiselectable
import org.thoughtcrime.securesms.conversation.v2.data.ConversationElementKey
import org.thoughtcrime.securesms.conversation.v2.data.ConversationMessageElement
import org.thoughtcrime.securesms.conversation.v2.data.ConversationUpdate
import org.thoughtcrime.securesms.conversation.v2.data.IncomingMedia
import org.thoughtcrime.securesms.conversation.v2.data.IncomingTextOnly
import org.thoughtcrime.securesms.conversation.v2.data.OutgoingMedia
import org.thoughtcrime.securesms.conversation.v2.data.OutgoingTextOnly
import org.thoughtcrime.securesms.conversation.v2.data.ThreadHeader
import org.thoughtcrime.securesms.conversation.v2.items.V2ConversationContext
import org.thoughtcrime.securesms.conversation.v2.items.V2TextOnlyViewHolder
import org.thoughtcrime.securesms.conversation.v2.items.bridge
import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.databinding.V2ConversationItemTextOnlyIncomingBinding
import org.thoughtcrime.securesms.databinding.V2ConversationItemTextOnlyOutgoingBinding
import org.thoughtcrime.securesms.giph.mp4.GiphyMp4PlaybackPolicyEnforcer
import org.thoughtcrime.securesms.groups.v2.GroupDescriptionUtil
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messagerequests.MessageRequestState
import org.thoughtcrime.securesms.mms.GlideRequests
import org.thoughtcrime.securesms.phonenumbers.PhoneNumberFormatter
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.CachedInflater
import org.thoughtcrime.securesms.util.HtmlUtil
import org.thoughtcrime.securesms.util.Projection
import org.thoughtcrime.securesms.util.ProjectionList
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder
import org.thoughtcrime.securesms.util.adapter.mapping.PagingMappingAdapter
import java.util.Locale
import java.util.Optional

class ConversationAdapterV2(
  private val lifecycleOwner: LifecycleOwner,
  private val glideRequests: GlideRequests,
  override val clickListener: ItemClickListener,
  private var hasWallpaper: Boolean,
  private val colorizer: Colorizer,
  private val startExpirationTimeout: (MessageRecord) -> Unit
) : PagingMappingAdapter<ConversationElementKey>(), ConversationAdapterBridge, V2ConversationContext {

  companion object {
    private val TAG = Log.tag(ConversationAdapterV2::class.java)
  }

  private val _selected = hashSetOf<MultiselectPart>()

  override val selectedItems: Set<MultiselectPart>
    get() = _selected.toSet()

  override var searchQuery: String? = null
  private var inlineContent: ConversationMessage? = null

  private var recordToPulse: ConversationMessage? = null
  private var pulseRequest: ConversationAdapterBridge.PulseRequest? = null

  private val condensedMode: ConversationItemDisplayMode? = null

  override var isMessageRequestAccepted: Boolean = false

  init {
    registerFactory(ThreadHeader::class.java, ::ThreadHeaderViewHolder, R.layout.conversation_item_thread_header)

    registerFactory(ConversationUpdate::class.java) { parent ->
      val view = CachedInflater.from(parent.context).inflate<View>(R.layout.conversation_item_update, parent, false)
      ConversationUpdateViewHolder(view)
    }

    registerFactory(OutgoingMedia::class.java) { parent ->
      val view = CachedInflater.from(parent.context).inflate<View>(R.layout.conversation_item_sent_multimedia, parent, false)
      OutgoingMediaViewHolder(view)
    }

    registerFactory(IncomingMedia::class.java) { parent ->
      val view = CachedInflater.from(parent.context).inflate<View>(R.layout.conversation_item_received_multimedia, parent, false)
      IncomingMediaViewHolder(view)
    }

    if (SignalStore.internalValues().useConversationItemV2()) {
      registerFactory(OutgoingTextOnly::class.java) { parent ->
        val view = CachedInflater.from(parent.context).inflate<View>(R.layout.v2_conversation_item_text_only_outgoing, parent, false)
        V2TextOnlyViewHolder(V2ConversationItemTextOnlyOutgoingBinding.bind(view).bridge(), this)
      }

      registerFactory(IncomingTextOnly::class.java) { parent ->
        val view = CachedInflater.from(parent.context).inflate<View>(R.layout.v2_conversation_item_text_only_incoming, parent, false)
        V2TextOnlyViewHolder(V2ConversationItemTextOnlyIncomingBinding.bind(view).bridge(), this)
      }
    } else {
      registerFactory(OutgoingTextOnly::class.java) { parent ->
        val view = CachedInflater.from(parent.context).inflate<View>(R.layout.conversation_item_sent_text_only, parent, false)
        OutgoingTextOnlyViewHolder(view)
      }

      registerFactory(IncomingTextOnly::class.java) { parent ->
        val view = CachedInflater.from(parent.context).inflate<View>(R.layout.conversation_item_received_text_only, parent, false)
        IncomingTextOnlyViewHolder(view)
      }
    }
  }

  override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
    super.onAttachedToRecyclerView(recyclerView)

    for ((model, type) in itemTypes) {
      val count: Int = when (model) {
        ThreadHeader::class.java -> 1
        ConversationUpdate::class.java -> 5
        OutgoingTextOnly::class.java -> 25
        OutgoingMedia::class.java -> 15
        IncomingTextOnly::class.java -> 25
        IncomingMedia::class.java -> 15
        Placeholder::class.java -> 5
        else -> 0
      }

      if (count > 0) {
        recyclerView.recycledViewPool.setMaxRecycledViews(type, count)
      }
    }
  }
  override val displayMode: ConversationItemDisplayMode
    get() = condensedMode ?: ConversationItemDisplayMode.STANDARD

  override fun onStartExpirationTimeout(messageRecord: MessageRecord) {
    startExpirationTimeout(messageRecord)
  }

  override fun hasWallpaper(): Boolean = hasWallpaper && displayMode.displayWallpaper()

  override fun getColorizer(): Colorizer = colorizer

  override fun getNextMessage(adapterPosition: Int): MessageRecord? {
    return getConversationMessage(adapterPosition - 1)?.messageRecord
  }

  override fun getPreviousMessage(adapterPosition: Int): MessageRecord? {
    return getConversationMessage(adapterPosition + 1)?.messageRecord
  }

  fun updateSearchQuery(searchQuery: String?) {
    this.searchQuery = searchQuery
    notifyItemRangeChanged(0, itemCount)
  }

  fun getLastVisibleConversationMessage(position: Int): ConversationMessage? {
    return try {
      getConversationMessage(position) ?: getConversationMessage(position - 1)
    } catch (e: IndexOutOfBoundsException) {
      Log.w(TAG, "Race condition changed size of conversation", e)
      null
    }
  }

  fun canJumpToPosition(absolutePosition: Int): Boolean {
    if (absolutePosition < 0) {
      return false
    }

    if (absolutePosition > super.getItemCount()) {
      Log.d(TAG, "Could not access corrected position $absolutePosition as it is out of bounds.")
      return false
    }

    if (!isRangeAvailable(absolutePosition - 10, absolutePosition + 5)) {
      getItem(absolutePosition)
      return false
    }

    return true
  }

  fun playInlineContent(conversationMessage: ConversationMessage?) {
    if (this.inlineContent !== conversationMessage) {
      this.inlineContent = conversationMessage
      notifyDataSetChanged()
    }
  }

  override fun getConversationMessage(position: Int): ConversationMessage? {
    return when (val item = getItem(position)) {
      is ConversationMessageElement -> item.conversationMessage
      is ThreadHeader -> null
      null -> null
      else -> throw AssertionError("Invalid item: ${item.javaClass}")
    }
  }

  override fun hasNoConversationMessages(): Boolean {
    return itemCount == 0
  }

  /**
   * Momentarily highlights a mention at the requested position.
   */
  fun pulseAtPosition(position: Int) {
    if (position in 0 until itemCount) {
      recordToPulse = getConversationMessage(position)
      if (recordToPulse != null) {
        pulseRequest = ConversationAdapterBridge.PulseRequest(position, recordToPulse!!.messageRecord.isOutgoing)
      }
      notifyItemChanged(position)
    }
  }

  override fun consumePulseRequest(): ConversationAdapterBridge.PulseRequest? {
    val request = pulseRequest
    pulseRequest = null
    return request
  }

  fun onHasWallpaperChanged(hasChanged: Boolean) {
    // todo [cody] implement
  }

  fun onMessageRequestStateChanged(isMessageRequestAccepted: Boolean) {
    val oldState = this.isMessageRequestAccepted
    this.isMessageRequestAccepted = isMessageRequestAccepted

    if (oldState != isMessageRequestAccepted) {
      notifyItemRangeChanged(0, itemCount)
    }
  }

  fun clearSelection() {
    _selected.clear()
  }

  fun toggleSelection(multiselectPart: MultiselectPart) {
    if (multiselectPart in _selected) {
      _selected.remove(multiselectPart)
    } else {
      _selected.add(multiselectPart)
    }
  }

  private inner class ConversationUpdateViewHolder(itemView: View) : ConversationViewHolder<ConversationUpdate>(itemView) {
    override fun bind(model: ConversationUpdate) {
      bindable.setEventListener(clickListener)
      bindable.bind(
        lifecycleOwner,
        model.conversationMessage,
        previousMessage,
        nextMessage,
        glideRequests,
        Locale.getDefault(),
        _selected,
        model.conversationMessage.threadRecipient,
        searchQuery,
        false,
        hasWallpaper && displayMode.displayWallpaper(),
        true, // isMessageRequestAccepted,
        model.conversationMessage == inlineContent,
        colorizer,
        displayMode
      )
    }
  }

  private inner class OutgoingTextOnlyViewHolder(itemView: View) : ConversationViewHolder<OutgoingTextOnly>(itemView) {
    override fun bind(model: OutgoingTextOnly) {
      bindable.setEventListener(clickListener)
      bindable.bind(
        lifecycleOwner,
        model.conversationMessage,
        previousMessage,
        nextMessage,
        glideRequests,
        Locale.getDefault(),
        _selected,
        model.conversationMessage.threadRecipient,
        searchQuery,
        false,
        hasWallpaper && displayMode.displayWallpaper(),
        true, // isMessageRequestAccepted,
        model.conversationMessage == inlineContent,
        colorizer,
        displayMode
      )
    }
  }

  private inner class OutgoingMediaViewHolder(itemView: View) : ConversationViewHolder<OutgoingMedia>(itemView) {
    override fun bind(model: OutgoingMedia) {
      bindable.setEventListener(clickListener)
      bindable.bind(
        lifecycleOwner,
        model.conversationMessage,
        previousMessage,
        nextMessage,
        glideRequests,
        Locale.getDefault(),
        _selected,
        model.conversationMessage.threadRecipient,
        searchQuery,
        false,
        hasWallpaper && displayMode.displayWallpaper(),
        true, // isMessageRequestAccepted,
        model.conversationMessage == inlineContent,
        colorizer,
        displayMode
      )
    }
  }

  private inner class IncomingTextOnlyViewHolder(itemView: View) : ConversationViewHolder<IncomingTextOnly>(itemView) {
    override fun bind(model: IncomingTextOnly) {
      bindable.setEventListener(clickListener)
      bindable.bind(
        lifecycleOwner,
        model.conversationMessage,
        previousMessage,
        nextMessage,
        glideRequests,
        Locale.getDefault(),
        _selected,
        model.conversationMessage.threadRecipient,
        searchQuery,
        false,
        hasWallpaper && displayMode.displayWallpaper(),
        true, // isMessageRequestAccepted,
        model.conversationMessage == inlineContent,
        colorizer,
        displayMode
      )
    }
  }

  private inner class IncomingMediaViewHolder(itemView: View) : ConversationViewHolder<IncomingMedia>(itemView) {
    override fun bind(model: IncomingMedia) {
      bindable.setEventListener(clickListener)
      bindable.bind(
        lifecycleOwner,
        model.conversationMessage,
        previousMessage,
        nextMessage,
        glideRequests,
        Locale.getDefault(),
        _selected,
        model.conversationMessage.threadRecipient,
        searchQuery,
        false,
        hasWallpaper && displayMode.displayWallpaper(),
        true, // isMessageRequestAccepted,
        model.conversationMessage == inlineContent,
        colorizer,
        displayMode
      )
    }
  }

  private abstract inner class ConversationViewHolder<T>(itemView: View) : MappingViewHolder<T>(itemView), Multiselectable, Colorizable {
    val bindable: BindableConversationItem
      get() = itemView as BindableConversationItem

    override val root: ViewGroup = bindable.root

    protected val previousMessage: Optional<MessageRecord>
      get() = getConversationMessage(bindingAdapterPosition + 1)?.messageRecord.toOptional()

    protected val nextMessage: Optional<MessageRecord>
      get() = getConversationMessage(bindingAdapterPosition - 1)?.messageRecord.toOptional()

    protected val displayMode: ConversationItemDisplayMode
      get() = condensedMode ?: ConversationItemDisplayMode.STANDARD

    override val conversationMessage: ConversationMessage
      get() = bindable.conversationMessage

    init {
      itemView.setOnClickListener {
        clickListener.onItemClick(bindable.getMultiselectPartForLatestTouch())
      }

      itemView.setOnLongClickListener {
        clickListener.onItemLongClick(
          it,
          bindable.getMultiselectPartForLatestTouch()
        )
        true
      }
    }

    override fun showProjectionArea() {
      bindable.showProjectionArea()
    }

    override fun hideProjectionArea() {
      bindable.hideProjectionArea()
    }

    override fun getMediaItem(): MediaItem? {
      return bindable.mediaItem
    }

    override fun getPlaybackPolicyEnforcer(): GiphyMp4PlaybackPolicyEnforcer? {
      return bindable.playbackPolicyEnforcer
    }

    override fun getGiphyMp4PlayableProjection(recyclerView: ViewGroup): Projection {
      return bindable.getGiphyMp4PlayableProjection(recyclerView)
    }

    override fun canPlayContent(): Boolean {
      return bindable.canPlayContent()
    }

    override fun shouldProjectContent(): Boolean {
      return bindable.shouldProjectContent()
    }

    override fun hasNonSelectableMedia(): Boolean = bindable.hasNonSelectableMedia()

    override fun getColorizerProjections(coordinateRoot: ViewGroup): ProjectionList = bindable.getColorizerProjections(coordinateRoot)

    override fun getTopBoundaryOfMultiselectPart(multiselectPart: MultiselectPart): Int = bindable.getTopBoundaryOfMultiselectPart(multiselectPart)

    override fun getBottomBoundaryOfMultiselectPart(multiselectPart: MultiselectPart): Int = bindable.getBottomBoundaryOfMultiselectPart(multiselectPart)

    override fun getHorizontalTranslationTarget(): View? = bindable.getHorizontalTranslationTarget()

    override fun getMultiselectPartForLatestTouch(): MultiselectPart = bindable.getMultiselectPartForLatestTouch()
  }

  inner class ThreadHeaderViewHolder(itemView: View) : MappingViewHolder<ThreadHeader>(itemView) {
    private val conversationBanner: ConversationHeaderView = itemView as ConversationHeaderView

    override fun bind(model: ThreadHeader) {
      val (recipient, groupInfo, sharedGroups, messageRequestState) = model.recipientInfo
      val isSelf = recipient.id == Recipient.self().id

      conversationBanner.setAvatar(glideRequests, recipient)
      conversationBanner.showBackgroundBubble(recipient.hasWallpaper())
      val title: String = conversationBanner.setTitle(recipient)
      conversationBanner.setAbout(recipient)

      if (recipient.isGroup) {
        if (groupInfo.pendingMemberCount > 0) {
          val invited = context.resources.getQuantityString(R.plurals.MessageRequestProfileView_invited, groupInfo.pendingMemberCount, groupInfo.pendingMemberCount)
          conversationBanner.setSubtitle(context.resources.getQuantityString(R.plurals.MessageRequestProfileView_members_and_invited, groupInfo.fullMemberCount, groupInfo.fullMemberCount, invited))
        } else if (groupInfo.fullMemberCount > 0) {
          conversationBanner.setSubtitle(context.resources.getQuantityString(R.plurals.MessageRequestProfileView_members, groupInfo.fullMemberCount, groupInfo.fullMemberCount))
        } else {
          conversationBanner.setSubtitle(null)
        }
      } else if (isSelf) {
        conversationBanner.setSubtitle(context.getString(R.string.ConversationFragment__you_can_add_notes_for_yourself_in_this_conversation))
      } else {
        val subtitle: String? = recipient.e164.map { e164: String? -> PhoneNumberFormatter.prettyPrint(e164!!) }.orElse(null)
        if (subtitle == null || subtitle == title) {
          conversationBanner.hideSubtitle()
        } else {
          conversationBanner.setSubtitle(subtitle)
        }
      }

      if (sharedGroups.isEmpty() || isSelf) {
        if (TextUtils.isEmpty(groupInfo.description)) {
          conversationBanner.setLinkifyDescription(false)
          conversationBanner.hideDescription()
        } else {
          conversationBanner.setLinkifyDescription(true)
          val linkifyWebLinks = messageRequestState == MessageRequestState.NONE
          conversationBanner.showDescription()

          GroupDescriptionUtil.setText(
            context,
            conversationBanner.description,
            groupInfo.description,
            linkifyWebLinks
          ) {
            clickListener.onShowGroupDescriptionClicked(recipient.getDisplayName(context), groupInfo.description, linkifyWebLinks)
          }
        }
      } else {
        val description: String = when (sharedGroups.size) {
          1 -> context.getString(R.string.MessageRequestProfileView_member_of_one_group, HtmlUtil.bold(sharedGroups[0]))
          2 -> context.getString(R.string.MessageRequestProfileView_member_of_two_groups, HtmlUtil.bold(sharedGroups[0]), HtmlUtil.bold(sharedGroups[1]))
          3 -> context.getString(R.string.MessageRequestProfileView_member_of_many_groups, HtmlUtil.bold(sharedGroups[0]), HtmlUtil.bold(sharedGroups[1]), HtmlUtil.bold(sharedGroups[2]))
          else -> {
            val others: Int = sharedGroups.size - 2
            context.getString(
              R.string.MessageRequestProfileView_member_of_many_groups,
              HtmlUtil.bold(sharedGroups[0]),
              HtmlUtil.bold(sharedGroups[1]),
              context.resources.getQuantityString(R.plurals.MessageRequestProfileView_member_of_d_additional_groups, others, others)
            )
          }
        }
        conversationBanner.setDescription(HtmlCompat.fromHtml(description, 0))
        conversationBanner.showDescription()
      }
    }
  }
}