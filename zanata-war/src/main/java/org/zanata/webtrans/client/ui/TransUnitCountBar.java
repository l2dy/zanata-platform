package org.zanata.webtrans.client.ui;

import org.zanata.common.ContentState;
import org.zanata.common.TranslationStats;
import org.zanata.webtrans.client.resources.WebTransMessages;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.event.dom.client.HasMouseOutHandlers;
import com.google.gwt.event.dom.client.HasMouseOverHandlers;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.LayoutPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class TransUnitCountBar extends Composite implements HasTranslationStats, HasMouseOverHandlers, HasMouseOutHandlers, HasClickHandlers
{

   private static TransUnitCountBarUiBinder uiBinder = GWT.create(TransUnitCountBarUiBinder.class);

   protected final TooltipPopupPanel tooltipPanel;
   private static final int TOTAL_WIDTH = 100;

   interface TransUnitCountBarUiBinder extends UiBinder<Widget, TransUnitCountBar>
   {
   }

   @UiField
   LayoutPanel layoutPanel;

   @UiField
   FlowPanel approvedPanel, needReviewPanel, untranslatedPanel, undefinedPanel, savedPanel;

   @UiField
   Label label;

   private final LabelFormat labelFormat;

   private final TranslationStats stats = new TranslationStats();

   private final WebTransMessages messages;

   private boolean statsByWords = true;

   @Inject
   public TransUnitCountBar(WebTransMessages messages, LabelFormat labelFormat, boolean enableClickToggle, boolean projectRequireReview)
   {
      this.messages = messages;
      this.labelFormat = labelFormat;

      tooltipPanel = new TooltipPopupPanel(projectRequireReview);

      initWidget(uiBinder.createAndBindUi(this));

      this.addMouseOutHandler(new MouseOutHandler()
      {
         @Override
         public void onMouseOut(MouseOutEvent event)
         {
            tooltipPanel.hide(true);
         }
      });

      this.addMouseOverHandler(new MouseOverHandler()
      {

         @Override
         public void onMouseOver(MouseOverEvent event)
         {
            tooltipPanel.showRelativeTo(layoutPanel);
         }
      });

      if (enableClickToggle)
      {
         this.addClickHandler(new ClickHandler()
         {
            @Override
            public void onClick(ClickEvent event)
            {
               setStatOption(!statsByWords);
            }
         });
      }

      sinkEvents(Event.ONMOUSEOVER | Event.ONMOUSEOUT | Event.ONCLICK);
   }

   public void setStatOption(boolean statsByWords)
   {
      this.statsByWords = statsByWords;
      refresh();
   }

   private void setupLayoutPanel(double undefinedLeft, double undefinedWidth, double approvedLeft, double approvedWidth, double savedLeft, double savedWidth, double needReviewLeft, double needReviewWidth, double untranslatedLeft, double untranslatedWidth)
   {
      layoutPanel.forceLayout();
      layoutPanel.setWidgetLeftWidth(undefinedPanel, undefinedLeft, Unit.PX, undefinedWidth, Unit.PX);
      layoutPanel.setWidgetLeftWidth(approvedPanel, approvedLeft, Unit.PX, approvedWidth, Unit.PX);
      layoutPanel.setWidgetLeftWidth(savedPanel, savedLeft, Unit.PX, savedWidth, Unit.PX);
      layoutPanel.setWidgetLeftWidth(needReviewPanel, needReviewLeft, Unit.PX, needReviewWidth, Unit.PX);
      layoutPanel.setWidgetLeftWidth(untranslatedPanel, untranslatedLeft, Unit.PX, untranslatedWidth, Unit.PX);
   }

   public void refresh()
   {
      int approved, needReview, untranslated, saved, total;
      if (statsByWords)
      {
         approved = getWordsApproved();
         needReview = getWordsNeedReview();
         untranslated = getWordsUntranslated();
         saved = getWordsSaved();
         total = getWordsTotal();
      }
      else
      {
         approved = getUnitApproved();
         needReview = getUnitNeedReview();
         untranslated = getUnitUntranslated();
         saved = getUnitSaved();
         total = getUnitTotal();
      }
      int width = getOffsetWidth();
      if (total == 0)
      {
         undefinedPanel.clear();
         undefinedPanel.add(new Label(messages.noContent()));
         setupLayoutPanel(0.0, width, 0, 0, 0.0, 0, 0.0, 0, 0.0, 0);
         label.setText("");
      }
      else
      {
         int completePx = approved * 100 / total * width / TOTAL_WIDTH;
         int savedPx = saved * 100 / total * width / TOTAL_WIDTH;
         int inProgressPx = needReview * 100 / total * width / TOTAL_WIDTH;
         int unfinishedPx = untranslated * 100 / total * width / TOTAL_WIDTH;

         int needReviewLeft = savedPx + completePx;
         int untranslatedLeft = needReviewLeft + inProgressPx;
         setupLayoutPanel(0.0, 0, 0.0, completePx, completePx, savedPx, needReviewLeft, inProgressPx, untranslatedLeft, unfinishedPx);
         setLabelText();
      }

      int duration = 600;

      tooltipPanel.refreshData(this);
      layoutPanel.animate(duration);
   }

   private void setLabelText()
   {
      // TODO rhbz953734 - remaining hours
      switch (labelFormat)
      {
      case PERCENT_COMPLETE_HRS:
         if (statsByWords)
         {
            label.setText(messages.statusBarPercentageHrs(stats.getApprovedPercent(statsByWords), stats.getRemainingHours(), "Words"));
         }
         else
         {
            label.setText(messages.statusBarPercentageHrs(stats.getApprovedPercent(statsByWords), stats.getRemainingHours(), "Msg"));
         }
         break;
      case PERCENT_COMPLETE:
         label.setText(messages.statusBarLabelPercentage(stats.getApprovedPercent(statsByWords)));
         break;
      default:
         label.setText("error: " + labelFormat.name());
      }
   }

   public int getWordsTotal()
   {
      return getWordsApproved() + getWordsNeedReview() + getWordsUntranslated() + getWordsSaved();
   }

   public int getWordsApproved()
   {
      return stats.getWordCount().get(ContentState.Approved);
   }

   public int getWordsNeedReview()
   {
      return stats.getWordCount().get(ContentState.NeedReview);
   }

   public int getWordsUntranslated()
   {
      return stats.getWordCount().get(ContentState.New);
   }

   public int getWordsSaved()
   {
      return stats.getWordCount().get(ContentState.Saved);
   }

   public int getUnitTotal()
   {
      return getUnitApproved() + getUnitNeedReview() + getUnitUntranslated() + getUnitSaved();
   }

   public int getUnitApproved()
   {
      return stats.getUnitCount().get(ContentState.Approved);
   }

   public int getUnitNeedReview()
   {
      return stats.getUnitCount().get(ContentState.NeedReview);
   }

   public int getUnitUntranslated()
   {
      return stats.getUnitCount().get(ContentState.New);
   }

   public int getUnitSaved()
   {
      return stats.getUnitCount().get(ContentState.Saved);
   }

   @Override
   public void setStats(TranslationStats stats, boolean statsByWords)
   {
      this.stats.set(stats);
      this.statsByWords = statsByWords;

      refresh();
   }

   @Override
   public int getOffsetWidth()
   {
      int offsetWidth = super.getOffsetWidth();
      return offsetWidth == 0 || offsetWidth > 100 ? 100 : offsetWidth;
   }

   @Override
   public HandlerRegistration addMouseOutHandler(MouseOutHandler handler)
   {
      return addDomHandler(handler, MouseOutEvent.getType());
   }

   @Override
   public HandlerRegistration addMouseOverHandler(MouseOverHandler handler)
   {
      return addDomHandler(handler, MouseOverEvent.getType());
   }

   @Override
   public HandlerRegistration addClickHandler(ClickHandler handler)
   {
      return addDomHandler(handler, ClickEvent.getType());
   }
}
