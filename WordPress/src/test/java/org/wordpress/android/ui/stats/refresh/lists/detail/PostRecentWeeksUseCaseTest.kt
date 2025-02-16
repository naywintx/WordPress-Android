package org.wordpress.android.ui.stats.refresh.lists.detail

import com.nhaarman.mockitokotlin2.KArgumentCaptor
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.wordpress.android.BaseUnitTest
import org.wordpress.android.R
import org.wordpress.android.TEST_DISPATCHER
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.stats.PostDetailStatsModel
import org.wordpress.android.fluxc.model.stats.PostDetailStatsModel.Day
import org.wordpress.android.fluxc.model.stats.PostDetailStatsModel.Month
import org.wordpress.android.fluxc.model.stats.PostDetailStatsModel.Week
import org.wordpress.android.fluxc.model.stats.PostDetailStatsModel.Year
import org.wordpress.android.fluxc.store.StatsStore.OnStatsFetched
import org.wordpress.android.fluxc.store.StatsStore.StatsError
import org.wordpress.android.fluxc.store.StatsStore.StatsErrorType.GENERIC_ERROR
import org.wordpress.android.fluxc.store.stats.PostDetailStore
import org.wordpress.android.test
import org.wordpress.android.ui.stats.refresh.lists.detail.PostDetailMapper.ExpandedWeekUiState
import org.wordpress.android.ui.stats.refresh.lists.sections.BaseStatsUseCase.UseCaseMode.BLOCK
import org.wordpress.android.ui.stats.refresh.lists.sections.BaseStatsUseCase.UseCaseModel
import org.wordpress.android.ui.stats.refresh.lists.sections.BaseStatsUseCase.UseCaseModel.UseCaseState.EMPTY
import org.wordpress.android.ui.stats.refresh.lists.sections.BaseStatsUseCase.UseCaseModel.UseCaseState.ERROR
import org.wordpress.android.ui.stats.refresh.lists.sections.BaseStatsUseCase.UseCaseModel.UseCaseState.SUCCESS
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.ExpandableItem
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Header
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Link
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.ListItemWithIcon
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Title
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Type.EXPANDABLE_ITEM
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Type.HEADER
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Type.LINK
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Type.LIST_ITEM_WITH_ICON
import org.wordpress.android.ui.stats.refresh.lists.sections.BlockListItem.Type.TITLE
import org.wordpress.android.ui.stats.refresh.utils.StatsPostProvider
import org.wordpress.android.ui.stats.refresh.utils.StatsSiteProvider
import org.wordpress.android.util.analytics.AnalyticsTrackerWrapper
import org.wordpress.android.viewmodel.ResourceProvider
import java.util.Calendar

class PostRecentWeeksUseCaseTest : BaseUnitTest() {
    @Mock lateinit var store: PostDetailStore
    @Mock lateinit var statsSiteProvider: StatsSiteProvider
    @Mock lateinit var statsPostProvider: StatsPostProvider
    @Mock lateinit var site: SiteModel
    @Mock lateinit var resourceProvider: ResourceProvider
    @Mock lateinit var tracker: AnalyticsTrackerWrapper
    @Mock lateinit var postDetailMapper: PostDetailMapper
    private lateinit var useCase: PostRecentWeeksUseCase
    private lateinit var expandCaptor: KArgumentCaptor<(ExpandedWeekUiState) -> Unit>
    private val postId: Long = 1L
    val firstDay = Day("2019-03-18", 50)
    val lastDay = Day("2019-03-24", 100)
    private val week = Week(listOf(firstDay, lastDay), 75, 150)
    @InternalCoroutinesApi
    @Before
    fun setUp() {
        useCase = PostRecentWeeksUseCase(
                Dispatchers.Unconfined,
                TEST_DISPATCHER,
                statsSiteProvider,
                statsPostProvider,
                store,
                postDetailMapper,
                BLOCK
        )
        expandCaptor = argumentCaptor()
        whenever(statsSiteProvider.siteModel).thenReturn(site)
        whenever(statsPostProvider.postId).thenReturn(postId)
    }

    @Test
    @Suppress("LongMethod")
    fun `maps years to UI model`() = test {
        val forced = false
        val data = listOf(week)
        val model = PostDetailStatsModel(0, listOf(), data, listOf(), listOf())
        whenever(store.getPostDetail(site, postId)).thenReturn(model)
        whenever(store.fetchPostDetail(site, postId, forced)).thenReturn(
                OnStatsFetched(
                        model
                )
        )
        val nonExpandedUiState = ExpandedWeekUiState()
        val lastDayCalendar = Calendar.getInstance()
        lastDayCalendar.set(2019, 3, 24)
        val expandedUiState = ExpandedWeekUiState(expandedWeekFirstDay = lastDayCalendar.time)
        whenever(
                postDetailMapper.mapWeeks(
                        eq(data),
                        eq(6),
                        eq(nonExpandedUiState),
                        any(),
                        expandCaptor.capture()
                )
        ).thenReturn(
                listOf(
                        ExpandableItem(
                                ListItemWithIcon(
                                        text = "Mar 18 - Mar 24, 2019",
                                        value = "150",
                                        contentDescription = "Mar: 150"
                                ), false
                        ) {
                            expandCaptor.lastValue.invoke(expandedUiState)
                        })
        )
        whenever(
                postDetailMapper.mapWeeks(
                        eq(data),
                        eq(6),
                        eq(expandedUiState),
                        any(),
                        expandCaptor.capture()
                )
        ).thenReturn(
                listOf(
                        ExpandableItem(
                                ListItemWithIcon(
                                        text = "Mar 18 - Mar 24, 2019",
                                        value = "150",
                                        contentDescription = "Mar: 150"
                                ),
                                false
                        ) { expandCaptor.lastValue.invoke(expandedUiState) },
                        ListItemWithIcon(text = "Mar 18", value = "50", contentDescription = "Mar: 50"),
                        ListItemWithIcon(text = "Mar 24", value = "100", contentDescription = "Mar: 100")
                )
        )

        val result = loadData(true, forced)

        assertThat(result.state).isEqualTo(SUCCESS)
        val expandableItem = result.data!!.assertNonExpandedList()

        expandableItem.onExpandClicked(true)

        val updatedResult = loadData(true, forced)

        updatedResult.data!!.assertExpandedList()
    }

    private fun List<BlockListItem>.assertNonExpandedList(): ExpandableItem {
        assertTitle(this[0])
        assertHeader(this[1])
        return assertWeek(this[2], "Mar 18 - Mar 24, 2019", 150)
    }

    private fun List<BlockListItem>.assertExpandedList() {
        assertTitle(this[0])
        assertHeader(this[1])
        assertWeek(this[2], "Mar 18 - Mar 24, 2019", 150)
        assertDay(this[3], "Mar 18", "50")
        assertDay(this[4], "Mar 24", "100")
    }

    @Test
    fun `adds view more button when hasMore`() = test {
        val forced = false
        val data = List(10) { week }

        val model = PostDetailStatsModel(0, listOf(), data, listOf(), listOf())
        whenever(store.getPostDetail(site, postId)).thenReturn(model)
        whenever(store.fetchPostDetail(site, postId, forced)).thenReturn(
                OnStatsFetched(
                        model
                )
        )
        val nonExpandedUiState = ExpandedWeekUiState()
        val lastDayCalendar = Calendar.getInstance()
        lastDayCalendar.set(2019, 3, 24)
        val expandedUiState = ExpandedWeekUiState(expandedWeekFirstDay = lastDayCalendar.time)
        whenever(
                postDetailMapper.mapWeeks(
                        eq(data),
                        eq(6),
                        eq(nonExpandedUiState),
                        any(),
                        expandCaptor.capture()
                )
        ).thenReturn(
                listOf(
                        ExpandableItem(
                                ListItemWithIcon(
                                        text = "Mar 18 - Mar 24, 2019",
                                        value = "150",
                                        contentDescription = "Mar: 150"
                                ), false
                        ) {
                            expandCaptor.lastValue.invoke(expandedUiState)
                        })
        )

        val result = loadData(true, forced)

        assertThat(result.state).isEqualTo(SUCCESS)
        result.data!!.apply {
            assertThat(this).hasSize(4)
            assertTitle(this[0])
            assertHeader(this[1])
            assertWeek(this[2], "Mar 18 - Mar 24, 2019", 150)
            assertLink(this[3])
        }
    }

    @Test
    fun `maps error item to UI model`() = test {
        val forced = false
        val message = "Generic error"
        whenever(store.fetchPostDetail(site, postId, forced)).thenReturn(
                OnStatsFetched(
                        StatsError(GENERIC_ERROR, message)
                )
        )

        val result = loadData(true, forced)

        assertThat(result.state).isEqualTo(ERROR)
    }

    @Test
    fun `maps list of empty items to empty UI model`() = test {
        val forced = false
        whenever(store.fetchPostDetail(site, postId, forced)).thenReturn(
                OnStatsFetched(
                        model = PostDetailStatsModel(
                                0,
                                listOf(Day("1970", 0), Day("1970", 1)),
                                listOf(Week(listOf(Day("Monday", 0)), 0, 0)),
                                listOf(Year(2020, listOf(Month(1, 55)), 10)),
                                listOf(Year(2020, listOf(), 10))
                        )
                )
        )

        val result = loadData(true, forced)

        assertThat(result.state).isEqualTo(EMPTY)
    }

    private fun assertTitle(item: BlockListItem) {
        assertThat(item.type).isEqualTo(TITLE)
        assertThat((item as Title).textResource).isEqualTo(R.string.stats_detail_recent_weeks)
    }

    private fun assertHeader(item: BlockListItem) {
        assertThat(item.type).isEqualTo(HEADER)
        assertThat((item as Header).startLabel).isEqualTo(R.string.stats_months_and_years_period_label)
        assertThat(item.endLabel).isEqualTo(R.string.stats_months_and_years_views_label)
    }

    private fun assertDay(
        item: BlockListItem,
        key: String,
        label: String?
    ) {
        assertThat(item.type).isEqualTo(LIST_ITEM_WITH_ICON)
        assertThat((item as ListItemWithIcon).text).isEqualTo(key)
        if (label != null) {
            assertThat(item.value).isEqualTo(label)
        } else {
            assertThat(item.value).isNull()
        }
    }

    private fun assertWeek(
        item: BlockListItem,
        label: String,
        views: Int
    ): ExpandableItem {
        assertThat(item.type).isEqualTo(EXPANDABLE_ITEM)
        assertThat((item as ExpandableItem).header.text).isEqualTo(label)
        assertThat(item.header.value).isEqualTo(views.toString())
        return item
    }

    private fun assertLink(item: BlockListItem) {
        assertThat(item.type).isEqualTo(LINK)
        assertThat((item as Link).text).isEqualTo(R.string.stats_insights_view_more)
    }

    private suspend fun loadData(refresh: Boolean, forced: Boolean): UseCaseModel {
        var result: UseCaseModel? = null
        useCase.liveData.observeForever { result = it }
        useCase.fetch(refresh, forced)
        return checkNotNull(result)
    }
}
