package no.kabina.kaboot.dispatcher;


import no.kabina.kaboot.KabootApplication;
import no.kabina.kaboot.cabs.Cab;
import no.kabina.kaboot.cabs.CabRepository;
import no.kabina.kaboot.orders.TaxiOrder;
import no.kabina.kaboot.orders.TaxiOrderRepository;
import no.kabina.kaboot.routes.LegRepository;
import no.kabina.kaboot.routes.RouteRepository;
import no.kabina.kaboot.stats.Stat;
import no.kabina.kaboot.stats.StatRepository;
import no.kabina.kaboot.stats.StatService;
import no.kabina.kaboot.stops.Stop;
import no.kabina.kaboot.stops.StopRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = KabootApplication.class)
@ActiveProfiles("test")
public class DispatcherServiceTests {

    @MockBean
    StatService statService;

    @MockBean
    StatRepository statRepo;

    @MockBean
    TaxiOrderRepository orderRepo;

    @MockBean
    CabRepository cabRepo;

    @MockBean
    RouteRepository routeRepo;

    @MockBean
    LegRepository legRepo;

    /*@MockBean
    StopRepository stopRepo;
*/
    @MockBean
    DistanceService distanceService;

    @Autowired
    DispatcherService service;

    @Autowired
    LcmUtil lcmUtil;

    @Before
    public void before() {
        given(statRepo.findByName("key")).willReturn(new Stat("key",1,0));
        given(statRepo.save(any())).willReturn(new Stat("key",2,0));
        //given(stopRepo.findAll()).willReturn(getAllStops());
        int numbOfStands = 50;
        given(distanceService.getDistances()).willReturn(PoolUtil.setCosts(numbOfStands));
    }

    @Test
    public void testRunLcmAndSolver() {
        TempModel model = genModel(50);
        TaxiOrder[] orders = model.getDemand();
        Cab[] cabs = model.getSupply();

        PoolUtil util = new PoolUtil(50);
        PoolElement[] pool = util.findPool(orders, 3);
        assertThat(pool.length).isSameAs(16);
        TaxiOrder[] demand = PoolUtil.findFirstLegInPoolOrLone(pool, orders);
        assertThat(demand.length).isSameAs(17);
        int [][] cost = lcmUtil.calculateCost("glpk.mod", "out.txt", demand, cabs);
        assertThat(cost.length).isSameAs(49);
        TempModel tempModel = service.runLcm(cabs, demand, cost, pool);
        assertThat(tempModel.getDemand().length).isSameAs(17);
        assertThat(tempModel.getSupply().length).isSameAs(49);
        // test solver by this occasion
        try {
            service.runSolver(cabs, demand, cost, pool);
        } catch (Exception e) {
            e.printStackTrace();
        }
        int[] x = service.readSolversResult(cost.length);
        assertThat(x.length - 2401).isSameAs(0);
    }

    @Test
    public void testGeneratePool() {
        final int numbOfStands = 20;
        TaxiOrder[] orders = new TaxiOrder[numbOfStands-1];
        for (int i = 0; i < numbOfStands-1; i++) {
            orders[i] = new TaxiOrder(i %2, (i+1)%2,20,20, true, TaxiOrder.OrderStatus.RECEIVED, null);
            orders[i].setId((long) i);
        }
        PoolElement[] pool = service.generatePool(orders);
        assertThat(pool.length).isSameAs(5);
    }

    @Test
    public void testPlan1() {
        service.findPlan(false);
        assertThat(service.hashCode()>0).isTrue();
    }

    @Test
    public void testPlan2() {
        TempModel model = genModel(10);
        TaxiOrder[] orders = model.getDemand();
        Cab[] cabs = model.getSupply();
        given(orderRepo.findByStatus(any())).willReturn(Arrays.asList(orders));
        given(cabRepo.findByStatus(any())).willReturn(Arrays.asList(cabs));
        service.findPlan(false);
        assertThat(service.hashCode()>0).isTrue();
    }

    private TempModel genModel(int size) {
        TaxiOrder[] orders;
        Cab[] cabs;
        final int numbOfStands = size;
        orders = new TaxiOrder[numbOfStands-1];
        cabs = new Cab[numbOfStands-1];
        for (int i = 0; i < numbOfStands-1; i++) {
            cabs[i] = new Cab(i, "", Cab.CabStatus.FREE);
            cabs[i].setId((long)i);
            orders[i] = new TaxiOrder(i %2, (i+1)%2,20,20, true, TaxiOrder.OrderStatus.RECEIVED, null);
            orders[i].setId((long)i);
        }
        return new TempModel(cabs, orders);
    }

    public static List<Stop> getAllStops() {
        List<Stop> stops = new ArrayList<>();
        for (int i=0; i<166; i++) {
            stops.add(new Stop(Long.parseLong(irvine[i][0]), // id
                    irvine[i][1], // no
                    irvine[i][2], // name
                    irvine[i][3], // type
                    irvine[i][4], // bearing
                    Double.parseDouble(irvine[i][6]),
                    Double.parseDouble(irvine[i][5])));
        }
        return stops;
    }

    private static String [][] irvine =
        {{"0","957","after","type","bearing","-4.6627426443694","55.6219447485564"},
        {"1","34","Ailsa Road","type","bearing","-4.67435038661459","55.6004339374193"},
        {"2","214","Annick Road","type","bearing","-4.64555959553072","55.6095171718899"},
        {"3","405","Annick Road","type","bearing","-4.64503995376814","55.6095823203479"},
        {"4","419","Annick Road","type","bearing","-4.65489335847737","55.6118141321415"},
        {"5","290","Annick Road","type","bearing","-4.65631370868291","55.6121518840446"},
        {"6","905","Ayr Road","type","bearing","-4.63939746551806","55.569093733005"},
        {"7","805","Ayr Road","type","bearing","-4.63924292288727","55.5696273891183"},
        {"8","66","Ayr Road","type","bearing","-4.64093982981363","55.5736356687437"},
        {"9","65","Ayr Road","type","bearing","-4.64099365425315","55.5742007881638"},
        {"10","69","Ayr Road","type","bearing","-4.64260993562538","55.5772400183009"},
        {"11","79","Ayr Road","type","bearing","-4.64249903065775","55.5772424109554"},
        {"12","70","Ayr Road","type","bearing","-4.64683023521891","55.5850138972074"},
        {"13","72","Ayr Road","type","bearing","-4.64725300032029","55.5854002589511"},
        {"14","181","Ayr Road","type","bearing","-4.65531091137969","55.5945020844481"},
        {"15","176","Ayr Road","type","bearing","-4.65510606887878","55.594524500968"},
        {"16","446","Ayr Road","type","bearing","-4.66349308032005","55.6009311006265"},
        {"17","445","Ayr Road","type","bearing","-4.66378391069077","55.6010056755718"},
        {"18","440","Ayr Road","type","bearing","-4.66562630935872","55.6035902782664"},
        {"19","332","Ayr Road","type","bearing","-4.66565065560798","55.6041829960519"},
        {"20","331","Ayr Road","type","bearing","-4.66638291853669","55.6056142280589"},
        {"21","420","Ayr Road","type","bearing","-4.66622740311094","55.6058962585085"},
        {"22","937","Bank Street","type","bearing","-4.66586338983043","55.6152702918768"},
        {"23","333","Bank Street","type","bearing","-4.66016301044588","55.6171649076215"},
        {"24","330","Bank Street","type","bearing","-4.65977929119518","55.6173619960452"},
        {"25","329","Bank Street","type","bearing","-4.6564594742421","55.6182969011337"},
        {"26","328","Bank Street","type","bearing","-4.65662354236252","55.6183742419574"},
        {"27","461","Beach Drive","type","bearing","-4.68584350359111","55.6071487774318"},
        {"28","958","before","type","bearing","-4.66271947523463","55.621837388861"},
        {"29","73","Brewster Place","type","bearing","-4.63759050289743","55.597967922353"},
        {"30","225","Brewster Place","type","bearing","-4.6389027442055","55.5993059240787"},
        {"31","125","Burns Crescent","type","bearing","-4.63549681057274","55.6263896971479"},
        {"32","130","Burns Crescent","type","bearing","-4.63548938490179","55.6265156956697"},
        {"33","261","Burns Street","type","bearing","-4.66963367328027","55.6186308617652"},
        {"34","362","Caldon Road","type","bearing","-4.66075361295836","55.6233002752808"},
        {"35","838","Caldon Road","type","bearing","-4.66078225430594","55.623488413641"},
        {"36","658","Castlepark","type","bearing","-4.65885139860911","55.6287526706219"},
        {"37","655","Castlepark","type","bearing","-4.66497746687689","55.6293567084583"},
        {"38","659","Castlepark","type","bearing","-4.65814988247667","55.6305925623594"},
        {"39","826","Castlepark","type","bearing","-4.66766410826501","55.6310150943843"},
        {"40","590","Castlepark","type","bearing","-4.66700935791277","55.6328360474395"},
        {"41","574","Castlepark","type","bearing","-4.65964728108095","55.633930796863"},
        {"42","589","Castlepark","type","bearing","-4.66400318685905","55.6348968740103"},
        {"43","779","Castlepark Road","type","bearing","-4.66891739616848","55.6298192979796"},
        {"44","895","Castlepark Road","type","bearing","-4.66932744589357","55.6300081193392"},
        {"45","776","Castlepark Road","type","bearing","-4.67130446387177","55.630342576734"},
        {"46","894","Castlepark Road","type","bearing","-4.67171514141895","55.6305403652088"},
        {"47","433","Clark Drive","type","bearing","-4.6535266500845","55.6148548990503"},
        {"48","99","Clark Drive","type","bearing","-4.65365776627926","55.6149149798965"},
        {"49","434","Clark Drive","type","bearing","-4.65076265174731","55.6169641024825"},
        {"50","100","Clark Drive","type","bearing","-4.65090720587978","55.616987941429"},
        {"51","103","Clark Drive","type","bearing","-4.65127632512611","55.6196315767488"},
        {"52","437","Clark Drive","type","bearing","-4.65113539715187","55.6196615908765"},
        {"53","907","Corsehill Mount Road","type","bearing","-4.63661513310692","55.6082717798523"},
        {"54","904","Corsehill Mount Road","type","bearing","-4.63691820940143","55.6082922240843"},
        {"55","843","Dick Terrace","type","bearing","-4.66073232076166","55.6265006703256"},
        {"56","1009","Dick Terrace","type","bearing","-4.65906766570881","55.6274895802798"},
        {"57","1023","Dick Terrace","type","bearing","-4.65900111877356","55.6276797837133"},
        {"58","337","Dickson Drive","type","bearing","-4.66163393695019","55.6276136572614"},
        {"59","544","Dickson Drive","type","bearing","-4.664122066647","55.6284584658686"},
        {"60","540","Dickson Drive","type","bearing","-4.66587445632425","55.628528231841"},
        {"61","891","Dickson Drive","type","bearing","-4.66813268109919","55.6287937020407"},
        {"62","289","Dickson Drive","type","bearing","-4.66874589903485","55.6289331601229"},
        {"63","796","Drybridge","type","bearing","-4.6051833573723","55.5966748284313"},
        {"64","522","Drybridge","type","bearing","-4.60538034571029","55.5970122007531"},
        {"65","818","East Road","type","bearing","-4.66400547712727","55.6152747383632"},
        {"66","809","East Road","type","bearing","-4.66604936919969","55.6163718424484"},
        {"67","416","Eglinton Street","type","bearing","-4.6688924672167","55.6173076992994"},
        {"68","915","Eglinton Street","type","bearing","-4.66870642392651","55.6173746703611"},
        {"69","515","Fullarton Street","type","bearing","-4.66675903526421","55.6085812687294"},
        {"70","254","Fullarton Street","type","bearing","-4.66700540907922","55.6087017471868"},
        {"71","511","Fullarton Street","type","bearing","-4.66844649545254","55.6109804464911"},
        {"72","510","Fullarton Street","type","bearing","-4.66887281339797","55.6114116051023"},
        {"73","257","Fullarton Street","type","bearing","-4.6720834564923","55.6130854384569"},
        {"74","260","Fullarton Street","type","bearing","-4.67142964103226","55.6132794631739"},
        {"75","23","Glenbervie Wynd","type","bearing","-4.65621589092365","55.6050710134158"},
        {"76","462","Gottries Road","type","bearing","-4.68004934473654","55.6083362886676"},
        {"77","795","Harbour Street","type","bearing","-4.68276006461547","55.6082679897108"},
        {"78","453","Heatherhouse Road","type","bearing","-4.66872026996037","55.6033251829613"},
        {"79","710","Heatherstane Way","type","bearing","-4.61745112147371","55.6159273179645"},
        {"80","295","High Street","type","bearing","-4.66545357774366","55.6141466437749"},
        {"81","348","High Street","type","bearing","-4.66583842934569","55.6144348961256"},
        {"82","753","High Street","type","bearing","-4.66633064948805","55.6144331764911"},
        {"83","754","High Street","type","bearing","-4.66609947771797","55.6145370803517"},
        {"84","347","High Street","type","bearing","-4.66656120141887","55.6145540004359"},
        {"85","342","High Street","type","bearing","-4.6673748522482","55.615075609228"},
        {"86","417","High Street","type","bearing","-4.6677755862246","55.6153635100687"},
        {"87","936","High Street","type","bearing","-4.66797138079986","55.6159075522866"},
        {"88","954","High Street","type","bearing","-4.66822390686631","55.6161177815844"},
        {"89","571","Kilwinning Road","type","bearing","-4.67098443915379","55.6209744297014"},
        {"90","564","Kilwinning Road","type","bearing","-4.6709137072108","55.6213355155268"},
        {"91","555","Kilwinning Road","type","bearing","-4.67220847244061","55.6233207463098"},
        {"92","556","Kilwinning Road","type","bearing","-4.67229578769596","55.6239031027884"},
        {"93","603","Kilwinning Road","type","bearing","-4.67397406631845","55.6270844294561"},
        {"94","684","Kilwinning Road","type","bearing","-4.67379956857891","55.6270882356613"},
        {"95","598","Kilwinning Road","type","bearing","-4.67443375196873","55.6284676370222"},
        {"96","597","Kilwinning Road","type","bearing","-4.67508558690408","55.6294062079926"},
        {"97","654","Kilwinning Road","type","bearing","-4.67526574777151","55.6301842861228"},
        {"98","471","Kilwinning Road","type","bearing","-4.67584254001944","55.6309537072888"},
        {"99","643","Kilwinning Road","type","bearing","-4.676403600583","55.6321908774537"},
        {"100","632","Kilwinning Road","type","bearing","-4.67720501552665","55.632991343087"},
        {"101","628","Kilwinning Road","type","bearing","-4.67802858690408","55.6343486136644"},
        {"102","627","Kilwinning Road","type","bearing","-4.67871472231551","55.6350886674349"},
        {"103","619","Kilwinning Road","type","bearing","-4.67929663266934","55.6361635740865"},
        {"104","30","Kirk Vennel","type","bearing","-4.66362214431546","55.6124336897216"},
        {"105","37","Kyle Road","type","bearing","-4.67279917346618","55.5995689217959"},
        {"106","169","Linkwood Place","type","bearing","-4.636078056869","55.632678128199"},
        {"107","297","Linkwood Place","type","bearing","-4.63617626896913","55.6327209586151"},
        {"108","109","Linkwood Place","type","bearing","-4.63583508449887","55.6335552358603"},
        {"109","108","Littlestane Rise","type","bearing","-4.63547519639844","55.6341112711691"},
        {"110","338","Livingston Terrace","type","bearing","-4.67024376268048","55.6278308987542"},
        {"111","335","Livingstone Terrace","type","bearing","-4.65536629635438","55.6242080949274"},
        {"112","341","Livingstone Terrace","type","bearing","-4.65836720506272","55.6248890998276"},
        {"113","277","Livingstone Terrace","type","bearing","-4.66059831172164","55.6252272013878"},
        {"114","844","Livingstone Terrace","type","bearing","-4.66110406433969","55.6254229600347"},
        {"115","340","Livingstone Terrace","type","bearing","-4.66386478336513","55.626073093393"},
        {"116","284","Livingstone Terrace","type","bearing","-4.66727905021754","55.6265201862796"},
        {"117","285","Livingstone Terrace","type","bearing","-4.67014667665761","55.6268712326464"},
        {"118","131","Lochlibo Road","type","bearing","-4.63825470526712","55.6286583875664"},
        {"119","439","Manson Road","type","bearing","-4.6521751984991","55.6218862306487"},
        {"120","438","Manson Road","type","bearing","-4.65231118924639","55.622018116125"},
        {"121","902","Middleton Road","type","bearing","-4.63749798108546","55.6233085397715"},
        {"122","901","Middleton Road","type","bearing","-4.63717011558362","55.6236301921592"},
        {"123","900","Middleton Road","type","bearing","-4.6321096835047","55.6253928506327"},
        {"124","998","Middleton Road","type","bearing","-4.63207593750652","55.6256003103738"},
        {"125","767","Mill Road","type","bearing","-4.65693903769779","55.6136484067792"},
        {"126","472","Mill Road","type","bearing","-4.65598997324583","55.6137049335074"},
        {"127","815","Mill Road","type","bearing","-4.65324631321041","55.614231769991"},
        {"128","762","Mill Road","type","bearing","-4.65306450165861","55.6143615458222"},
        {"129","163","Mill Road","type","bearing","-4.64791809456711","55.6150571054115"},
        {"130","768","Mill Road","type","bearing","-4.64773020955335","55.6150971189557"},
        {"131","300","Millburn Terrace","type","bearing","-4.63499155152455","55.6337711170969"},
        {"132","301","Millburn Terrace","type","bearing","-4.63460854979936","55.6342197846508"},
        {"133","812","Montgomery Street","type","bearing","-4.67676954586878","55.6107719806852"},
        {"134","478","Montgomery Street","type","bearing","-4.67683786836016","55.6108423974999"},
        {"135","481","New Street","type","bearing","-4.67424047040311","55.6114114524309"},
        {"136","272","Paterson Avenue","type","bearing","-4.65372124853959","55.6231381295554"},
        {"137","839","Paterson Avenue","type","bearing","-4.65364982611031","55.623256526952"},
        {"138","336","Paterson Avenue","type","bearing","-4.65229985299517","55.6244362789899"},
        {"139","999","Paterson Avenue","type","bearing","-4.65531554667448","55.6265102643263"},
        {"140","1008","Paterson Avenue","type","bearing","-4.65480123150445","55.6264225301325"},
        {"141","355","Quarry Road","type","bearing","-4.66761211399681","55.6183333075458"},
        {"142","334","Quarry Road","type","bearing","-4.66748096426056","55.6185069456613"},
        {"143","356","Quarry Road","type","bearing","-4.66476144232016","55.6203278703518"},
        {"144","837","Quarry Road","type","bearing","-4.66475776967876","55.6205077221229"},
        {"145","923","Riverside Way","type","bearing","-4.64197619613584","55.602987910543"},
        {"146","922","Riverside Way","type","bearing","-4.64215904232462","55.6031098074514"},
        {"147","41","Riverside Way","type","bearing","-4.63821739314387","55.6032846221479"},
        {"148","916","Riverside Way","type","bearing","-4.6452404013375","55.6047781142311"},
        {"149","221","Riverside Way","type","bearing","-4.64547083030067","55.6048989792087"},
        {"150","400","Riverside Way","type","bearing","-4.64427259126736","55.6078551048751"},
        {"151","399","Riverside Way","type","bearing","-4.64438464802496","55.6081043657453"},
        {"152","773","Riverway","type","bearing","-4.67172132770198","55.6082304929765"},
        {"153","33","Sandy Road","type","bearing","-4.67759802656079","55.6285064264757"},
        {"154","223","Shewalton Road","type","bearing","-4.63931227100683","55.5971308654864"},
        {"155","271","Stewart Drive","type","bearing","-4.65375019176278","55.6223914534699"},
        {"156","339","Stewart Drive","type","bearing","-4.65632325522661","55.6230907564454"},
        {"157","266","Stewart Drive","type","bearing","-4.65725298310319","55.6232144213094"},
        {"158","265","Stewart Drive","type","bearing","-4.65940948473446","55.6238597713289"},
        {"159","365","Stewart Drive","type","bearing","-4.66012937846194","55.6239340358666"},
        {"160","224","Symington Place","type","bearing","-4.64005446415924","55.5984901220435"},
        {"161","427","Townhead","type","bearing","-4.65927759116902","55.6132381406326"},
        {"162","428","Townhead","type","bearing","-4.65899883290636","55.6133430635627"},
        {"163","325","Townhead","type","bearing","-4.66233445895314","55.6135852439275"},
        {"164","326","Townhead","type","bearing","-4.66213499751045","55.6136884513495"},
        {"165","480","Victoria Roundabout","type","bearing","-4.67559179052223","55.6135302407958"}};
}
