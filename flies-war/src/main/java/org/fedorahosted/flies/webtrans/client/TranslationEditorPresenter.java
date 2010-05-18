package org.fedorahosted.flies.webtrans.client;

import org.fedorahosted.flies.common.ContentState;
import org.fedorahosted.flies.common.TransUnitCount;
import org.fedorahosted.flies.webtrans.client.editor.HasTransUnitCount;
import org.fedorahosted.flies.webtrans.client.editor.table.TableEditorPresenter;
import org.fedorahosted.flies.webtrans.client.events.DocumentSelectionEvent;
import org.fedorahosted.flies.webtrans.client.events.DocumentSelectionHandler;
import org.fedorahosted.flies.webtrans.client.events.TransUnitUpdatedEvent;
import org.fedorahosted.flies.webtrans.client.events.TransUnitUpdatedEventHandler;
import org.fedorahosted.flies.webtrans.client.rpc.CachingDispatchAsync;
import org.fedorahosted.flies.webtrans.client.ui.HasPager;
import org.fedorahosted.flies.webtrans.shared.model.DocumentId;
import org.fedorahosted.flies.webtrans.shared.model.DocumentInfo;
import org.fedorahosted.flies.webtrans.shared.model.TransUnit;
import org.fedorahosted.flies.webtrans.shared.rpc.GetStatusCount;
import org.fedorahosted.flies.webtrans.shared.rpc.GetStatusCountResult;

import com.allen_sauer.gwt.log.client.Log;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.gen2.table.event.client.PageChangeEvent;
import com.google.gwt.gen2.table.event.client.PageChangeHandler;
import com.google.gwt.gen2.table.event.client.PageCountChangeEvent;
import com.google.gwt.gen2.table.event.client.PageCountChangeHandler;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

import net.customware.gwt.dispatch.client.DispatchAsync;
import net.customware.gwt.presenter.client.EventBus;
import net.customware.gwt.presenter.client.place.Place;
import net.customware.gwt.presenter.client.place.PlaceRequest;
import net.customware.gwt.presenter.client.widget.WidgetDisplay;
import net.customware.gwt.presenter.client.widget.WidgetPresenter;

public class TranslationEditorPresenter extends
		WidgetPresenter<TranslationEditorPresenter.Display> {

	public interface Display extends WidgetDisplay {

		void setTranslationMemoryView(Widget translationMemoryView);

		void setEditorView(Widget widget);

		void setTransUnitNavigation(Widget widget);

		void setTmViewVisible(boolean visible);
		
		HasTransUnitCount getTransUnitCount();

		HasPager getPageNavigation();

		HasClickHandlers getHideTMViewButton();
		HasClickHandlers getShowTMViewButton();
		void setShowTMViewButtonVisible(boolean visible);

	}

	private final TransUnitNavigationPresenter transUnitNavigationPresenter;
	private final TransMemoryPresenter transMemoryPresenter;
	private final TableEditorPresenter tableEditorPresenter;

	private DocumentInfo currentDocument;
	private final TransUnitCount statusCount = new TransUnitCount();
	
	private final DispatchAsync dispatcher;
	@Inject
	public TranslationEditorPresenter(Display display, EventBus eventBus,
			final CachingDispatchAsync dispatcher,
			final TransMemoryPresenter transMemoryPresenter,
			final TableEditorPresenter tableEditorPresenter,
			final TransUnitNavigationPresenter transUnitNavigationPresenter) {
		super(display, eventBus);
		this.dispatcher = dispatcher;
		this.transMemoryPresenter = transMemoryPresenter;
		this.tableEditorPresenter = tableEditorPresenter;
		this.transUnitNavigationPresenter = transUnitNavigationPresenter;
	}

	@Override
	public Place getPlace() {
		return null;
	}

	@Override
	protected void onBind() {
		transMemoryPresenter.bind();
		display.setTranslationMemoryView(transMemoryPresenter.getDisplay()
				.asWidget());

		tableEditorPresenter.bind();
		display.setEditorView(tableEditorPresenter.getDisplay().asWidget());

		transUnitNavigationPresenter.bind();
		display.setTransUnitNavigation(transUnitNavigationPresenter
				.getDisplay().asWidget());

		registerHandler(display.getPageNavigation().addValueChangeHandler(
				new ValueChangeHandler<Integer>() {
					@Override
					public void onValueChange(ValueChangeEvent<Integer> event) {
						tableEditorPresenter.cancelEdit();
						tableEditorPresenter.gotoPage(event.getValue() - 1,
								false);
					}
				}));

		// TODO this uses incubator's HandlerRegistration
		tableEditorPresenter.addPageChangeHandler(new PageChangeHandler() {
			@Override
			public void onPageChange(PageChangeEvent event) {
				display.getPageNavigation().setValue(event.getNewPage() + 1);
			}
		});

		// TODO this uses incubator's HandlerRegistration
		tableEditorPresenter
				.addPageCountChangeHandler(new PageCountChangeHandler() {
					@Override
					public void onPageCountChange(PageCountChangeEvent event) {
						display.getPageNavigation().setPageCount(
								event.getNewPageCount());
					}
				});

		registerHandler(eventBus.addHandler(DocumentSelectionEvent.getType(), new DocumentSelectionHandler() {
			@Override
			public void onDocumentSelected(DocumentSelectionEvent event) {
				if(currentDocument != null && currentDocument.getId().equals(event.getDocument().getId())){
					return;
				}
				currentDocument = event.getDocument();
				requestStatusCount(event.getDocument().getId());
			}
		}));
		registerHandler(
				eventBus.addHandler(TransUnitUpdatedEvent.getType(), updateHandler)
		);
		
		registerHandler( display.getHideTMViewButton().addClickHandler( new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				display.setTmViewVisible(false);
				transMemoryPresenter.unbind();
				display.setShowTMViewButtonVisible(true);
			}
		}));
		
		display.setShowTMViewButtonVisible(false);
		display.getShowTMViewButton().addClickHandler( new ClickHandler() {
			@Override
			public void onClick(ClickEvent event) {
				transMemoryPresenter.bind();
				display.setTmViewVisible(true);
				display.setShowTMViewButtonVisible(false);
				TransUnit tu = tableEditorPresenter.getSelectedTransUnit();
				if(tu != null) {
					transMemoryPresenter.showResultsFor( tu );
				}
			}
		});
		
	}

	private void requestStatusCount(final DocumentId newDocumentId) {
		dispatcher.execute(new GetStatusCount(newDocumentId), new AsyncCallback<GetStatusCountResult>() {
			@Override
			public void onFailure(Throwable caught) {
				Log.error("error fetching GetStatusCount: " + caught.getMessage());
			}
			@Override
			public void onSuccess(GetStatusCountResult result) {
				statusCount.set(result.getCount());
				display.getTransUnitCount().setCount(statusCount);
			}
	});
	}	

	private final TransUnitUpdatedEventHandler updateHandler = new TransUnitUpdatedEventHandler() {
		@Override
		public void onTransUnitUpdated(TransUnitUpdatedEvent event) {
			if(currentDocument == null){
				return;
			}
			if(!event.getDocumentId().equals( currentDocument.getId() )){
				return;
			}
			
			statusCount.increment(event.getNewStatus());
			statusCount.decrement(event.getPreviousStatus());
			
			display.getTransUnitCount().setCount(statusCount);
			
		}
	};	
	
	
	@Override
	protected void onPlaceRequest(PlaceRequest request) {
	}

	@Override
	protected void onUnbind() {
		transMemoryPresenter.unbind();
		tableEditorPresenter.unbind();
		transUnitNavigationPresenter.unbind();
	}

	@Override
	public void refreshDisplay() {
	}

	@Override
	public void revealDisplay() {
	}

}
